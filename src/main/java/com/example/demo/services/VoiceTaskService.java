package com.example.demo.services;

import com.example.demo.dto.request.TaskRequest;
import com.example.demo.dto.request.VoiceTaskConfirmRequest;
import com.example.demo.dto.response.TaskResponse;
import com.example.demo.dto.response.VoiceAssigneeCandidateResponse;
import com.example.demo.dto.response.VoiceTaskDraftResponse;
import com.example.demo.entity.Project;
import com.example.demo.entity.ProjectMember;
import com.example.demo.entity.User;
import com.example.demo.enums.Priority;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.llm.VoiceIntentParserService;
import com.example.demo.services.llm.VoiceParseResult;
import com.example.demo.services.stt.SpeechToTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VoiceTaskService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Pattern DEADLINE_ISO = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern DEADLINE_DD_MM_YYYY = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})");
    private static final Pattern DEADLINE_DD_MM = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})(?![./-]\\d)");
    private static final Pattern DEADLINE_RU_MONTH = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s+(январ[ьяе]?|феврал[ьяе]?|март[ае]?|апрел[ьяе]?|ма[йяе]|июн[ьяе]?|июл[ьяе]?|август[ае]?|сентябр[ьяе]?|октябр[ьяе]?|ноябр[ьяе]?|декабр[ьяе]?)(?:\\s+(\\d{4}))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DEADLINE_WEEKDAY = Pattern.compile(
            "\\b(понедельник(?:а|у|ом|е)?|вторник(?:а|у|ом|е)?|сред(?:а|у|ой|е)|четверг(?:а|у|ом|е)?|пятниц(?:а|у|ей|е)|суббот(?:а|у|ой|е)|воскресень(?:е|я|ю|ем))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ProjectService projectService;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final SpeechToTextService speechToTextService;
    private final VoiceIntentParserService voiceIntentParserService;

    public VoiceTaskDraftResponse parse(Long projectId,
            MultipartFile audio,
            String transcriptHint,
            String email) {

        Project project = projectService.getById(projectId, email);

        String transcript = resolveTranscript(audio, transcriptHint);
        if (transcript == null || transcript.isBlank()) {
            throw new IllegalArgumentException(
                    "Не удалось распознать речь. Передайте transcript или настройте STT-провайдер");
        }

        VoiceTaskDraftResponse draft = new VoiceTaskDraftResponse();
        draft.setTranscript(transcript.trim());

        List<User> projectUsers = getProjectUsers(project);

        // AI parse
        VoiceParseResult aiParsed;
        try {
            aiParsed = voiceIntentParserService.parse(transcript);
            if (aiParsed != null) {
                draft.getWarnings().add("Текст обработан ИИ");
            } else {
                throw new IllegalStateException("ИИ парсинг вернул пустой результат");
            }
        } catch (Exception ex) {
            log.error("[VoiceTask] AI parse error: {}", ex.getMessage(), ex);
            throw new IllegalStateException("ИИ парсинг недоступен: " + ex.getMessage(), ex);
        }

        String title = safe(aiParsed != null ? aiParsed.title() : null);
        title = sanitizeExtractedValue(title);

        String description = safe(aiParsed != null ? aiParsed.description() : null);
        description = sanitizeExtractedValue(description);

        String deadlineRaw = safe(aiParsed != null ? aiParsed.dueDateRaw() : null);
        LocalDate deadline = extractDeadline(deadlineRaw);

        String priorityRaw = safe(aiParsed != null ? aiParsed.priorityRaw() : null);
        Priority detectedPriority = parsePriority(priorityRaw);

        String assigneeRaw = safe(aiParsed.assigneeRaw());
        assigneeRaw = sanitizeExtractedValue(assigneeRaw);
        assigneeRaw = truncateAtActionWords(assigneeRaw);

        title = sanitizeExtractedValue(title);
        if (looksLikeWholeCommand(title, transcript) || title.length() > 140) {
            title = "";
        }

        description = sanitizeExtractedValue(description);
        if (looksLikeWholeCommand(description, transcript)) {
            description = "";
        }

        draft.setAssigneeRaw(assigneeRaw);

        List<VoiceAssigneeCandidateResponse> candidates = resolveAssigneeCandidates(assigneeRaw, projectUsers);
        draft.setAssigneeCandidates(candidates);

        if (!candidates.isEmpty()) {
            VoiceAssigneeCandidateResponse best = candidates.get(0);
            double second = candidates.size() > 1 ? candidates.get(1).getScore() : 0.0;
            if (best.getScore() >= 0.78 && (candidates.size() == 1 || (best.getScore() - second) >= 0.18)) {
                draft.setAssigneeId(best.getId());
            } else {
                draft.getWarnings().add("Найдено несколько кандидатов исполнителя — выберите вручную");
                draft.getMissingFields().add("assigneeId");
            }
        } else {
            draft.getWarnings().add("Не удалось надёжно определить исполнителя");
            draft.getMissingFields().add("assigneeId");
        }

        if (deadline != null) {
            draft.setDueDate(deadline.format(ISO));
        } else {
            draft.getWarnings().add("Дедлайн не найден в голосовой команде");
            draft.getMissingFields().add("dueDate");
        }

        if (detectedPriority != null) {
            draft.setPriority(detectedPriority.name());
        } else if (!priorityRaw.isBlank()) {
            draft.getWarnings().add("Не удалось распознать приоритетность, будет MEDIUM");
        }

        if (title == null || title.isBlank()) {
            draft.getMissingFields().add("title");
            draft.getWarnings().add("Не удалось извлечь название задачи");
        } else {
            draft.setTitle(title);
        }

        draft.setDescription(description);

        double confidence = computeConfidence(draft);
        if (aiParsed != null && aiParsed.confidence() != null) {
            confidence = Math.max(confidence, Math.max(0.0, Math.min(1.0, aiParsed.confidence())));
        }
        draft.setConfidence(confidence);

        if (confidence < 0.65) {
            draft.getWarnings().add("Низкий confidence распознавания — проверьте все поля перед созданием");
        }

        return draft;
    }

    public TaskResponse confirm(Long projectId, VoiceTaskConfirmRequest request, String email) {
        // Проверка доступа к проекту на уровне существующей логики
        projectService.getById(projectId, email);

        // confidence check removed: пользователь явно подтвердил данные вручную

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setProjectId(projectId);
        taskRequest.setTitle(request.getTitle());
        taskRequest.setDescription(request.getDescription());
        taskRequest.setDueDate(request.getDueDate());
        taskRequest.setPriority(request.getPriority());
        taskRequest.setAssigneeIds(List.of(request.getAssigneeId()));

        // Ключевой момент: переиспользуем существующий create flow без дублирования
        return taskService.createTask(taskRequest, email);
    }

    private String resolveTranscript(MultipartFile audio, String transcriptHint) {
        if (transcriptHint != null && !transcriptHint.isBlank()) {
            return transcriptHint;
        }
        if (audio == null || audio.isEmpty()) {
            return null;
        }
        return speechToTextService.transcribe(audio);
    }

    private List<User> getProjectUsers(Project project) {
        Set<Long> ids = new LinkedHashSet<>();
        if (project.getPm() != null) {
            ids.add(project.getPm().getId());
        }
        if (project.getMembers() != null) {
            for (ProjectMember m : project.getMembers()) {
                if (m.getUser() != null) {
                    ids.add(m.getUser().getId());
                }
            }
        }

        List<User> byIds = ids.isEmpty()
                ? new ArrayList<>()
                : userRepository.findAllById(ids).stream().filter(User::isActive).toList();

        List<User> deptUsers = new ArrayList<>();
        if (project.getDepartment() != null) {
            deptUsers = userRepository.findActiveByDepartmentIdWithDepartment(project.getDepartment().getId());
        }

        Set<Long> merged = new LinkedHashSet<>();
        List<User> result = new ArrayList<>();
        for (User u : byIds) {
            if (merged.add(u.getId()))
                result.add(u);
        }
        for (User u : deptUsers) {
            if (merged.add(u.getId()))
                result.add(u);
        }
        if (result.isEmpty()) {
            throw new ResourceNotFoundException("В проекте нет доступных исполнителей");
        }
        return result;
    }

    private List<VoiceAssigneeCandidateResponse> resolveAssigneeCandidates(String assigneeRaw, List<User> users) {
        if (assigneeRaw == null || assigneeRaw.isBlank()) {
            return List.of();
        }

        String q = normalizePersonText(assigneeRaw);
        if (q.isBlank()) {
            return List.of();
        }

        List<VoiceAssigneeCandidateResponse> candidates = new ArrayList<>();

        for (User u : users) {
            String full = normalize(u.getFullName());
            double score = scoreNameMatch(q, full);
            if (score >= 0.35) {
                candidates.add(new VoiceAssigneeCandidateResponse(u.getId(), u.getFullName(), score));
            }
        }

        candidates.sort(Comparator.comparingDouble(VoiceAssigneeCandidateResponse::getScore).reversed());
        return candidates.stream().limit(5).toList();
    }

    private double scoreNameMatch(String q, String full) {
        if (q.equals(full))
            return 1.0;

        String[] qTokens = q.split(" ");
        String[] fTokens = full.split(" ");

        int strongHit = 0;
        double fuzzySum = 0.0;

        for (String qt : qTokens) {
            if (qt.isBlank())
                continue;

            double best = 0.0;
            for (String ft : fTokens) {
                if (ft.isBlank())
                    continue;
                double local;
                if (ft.startsWith(qt) || qt.startsWith(ft) || ft.contains(qt)) {
                    local = 1.0;
                } else {
                    int d = levenshtein(qt, ft);
                    int max = Math.max(qt.length(), ft.length());
                    local = max == 0 ? 0.0 : Math.max(0.0, 1.0 - ((double) d / max));
                }
                if (local > best)
                    best = local;
            }

            if (best >= 0.85)
                strongHit++;
            fuzzySum += best;
        }

        double strongRatio = (double) strongHit / Math.max(1, qTokens.length);
        double fuzzyRatio = fuzzySum / Math.max(1, qTokens.length);

        double result = 0.65 * strongRatio + 0.35 * fuzzyRatio;
        if (full.contains(q))
            result = Math.max(result, 0.9);
        return Math.min(1.0, result);
    }

    private LocalDate extractDeadline(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }

        LocalDate now = LocalDate.now();
        String normalized = normalizeDeadlineText(transcript);

        if (normalized.contains("послезавтра")) {
            return now.plusDays(2);
        }
        if (normalized.contains("завтра")) {
            return now.plusDays(1);
        }
        if (normalized.contains("сегодня")) {
            return now;
        }

        Matcher isoMatcher = DEADLINE_ISO.matcher(normalized);
        if (isoMatcher.find()) {
            try {
                return LocalDate.parse(isoMatcher.group(1), ISO);
            } catch (DateTimeParseException ignored) {
            }
        }

        Matcher m1 = DEADLINE_DD_MM_YYYY.matcher(transcript);
        if (m1.find()) {
            int d = Integer.parseInt(m1.group(1));
            int m = Integer.parseInt(m1.group(2));
            int y = Integer.parseInt(m1.group(3));
            if (y < 100)
                y += 2000;
            try {
                return LocalDate.of(y, m, d);
            } catch (Exception ignored) {
            }
        }

        Matcher m2 = DEADLINE_DD_MM.matcher(normalized);
        if (m2.find()) {
            try {
                int d = Integer.parseInt(m2.group(1));
                int m = Integer.parseInt(m2.group(2));
                LocalDate date = LocalDate.of(now.getYear(), m, d);
                if (date.isBefore(now)) {
                    date = date.plusYears(1);
                }
                return date;
            } catch (Exception ignored) {
            }
        }

        Matcher m3 = DEADLINE_RU_MONTH.matcher(normalized);
        if (m3.find()) {
            try {
                int d = Integer.parseInt(m3.group(1));
                Month month = resolveRuMonth(m3.group(2));
                if (month != null) {
                    int year = (m3.group(3) != null && !m3.group(3).isBlank())
                            ? Integer.parseInt(m3.group(3))
                            : now.getYear();
                    LocalDate date = LocalDate.of(year, month, d);
                    if (m3.group(3) == null && date.isBefore(now)) {
                        date = date.plusYears(1);
                    }
                    return date;
                }
            } catch (Exception ignored) {
            }
        }

        Matcher m4 = DEADLINE_WEEKDAY.matcher(normalized);
        if (m4.find()) {
            DayOfWeek dayOfWeek = resolveRuDayOfWeek(m4.group(1));
            if (dayOfWeek != null) {
                return now.with(TemporalAdjusters.nextOrSame(dayOfWeek));
            }
        }

        return null;
    }

    private String normalizeDeadlineText(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        return normalized
                .replaceAll("(?i)\\b(?:дедлайн(?:\\s*до)?|срок(?:\\s*до)?|крайний\\s+срок)\\b", " ")
                .replaceAll("(?i)^\\s*(?:до|к)\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Month resolveRuMonth(String rawMonth) {
        if (rawMonth == null || rawMonth.isBlank()) {
            return null;
        }
        String m = rawMonth.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        if (m.startsWith("январ"))
            return Month.JANUARY;
        if (m.startsWith("феврал"))
            return Month.FEBRUARY;
        if (m.startsWith("март"))
            return Month.MARCH;
        if (m.startsWith("апрел"))
            return Month.APRIL;
        if (m.startsWith("май") || m.startsWith("мая") || m.startsWith("мае"))
            return Month.MAY;
        if (m.startsWith("июн"))
            return Month.JUNE;
        if (m.startsWith("июл"))
            return Month.JULY;
        if (m.startsWith("август"))
            return Month.AUGUST;
        if (m.startsWith("сентябр"))
            return Month.SEPTEMBER;
        if (m.startsWith("октябр"))
            return Month.OCTOBER;
        if (m.startsWith("ноябр"))
            return Month.NOVEMBER;
        if (m.startsWith("декабр"))
            return Month.DECEMBER;
        return null;
    }

    private DayOfWeek resolveRuDayOfWeek(String rawDay) {
        if (rawDay == null || rawDay.isBlank()) {
            return null;
        }
        String d = rawDay.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        if (d.startsWith("понедельник"))
            return DayOfWeek.MONDAY;
        if (d.startsWith("вторник"))
            return DayOfWeek.TUESDAY;
        if (d.startsWith("сред"))
            return DayOfWeek.WEDNESDAY;
        if (d.startsWith("четверг"))
            return DayOfWeek.THURSDAY;
        if (d.startsWith("пятниц"))
            return DayOfWeek.FRIDAY;
        if (d.startsWith("суббот"))
            return DayOfWeek.SATURDAY;
        if (d.startsWith("воскресень"))
            return DayOfWeek.SUNDAY;
        return null;
    }

    private Priority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String p = normalize(raw);
        if (p.contains("выс") || p.contains("high") || p.contains("крит") || p.contains("сроч") || p.contains("важн")
                || p.contains("первостеп")) {
            return Priority.HIGH;
        }
        if (p.contains("низ") || p.contains("low") || p.contains("несроч") || p.contains("неважн")) {
            return Priority.LOW;
        }
        if (p.contains("сред") || p.contains("mid") || p.contains("normal") || p.contains("обыч")) {
            return Priority.MEDIUM;
        }
        return null;
    }

    private boolean looksLikeWholeCommand(String value, String transcript) {
        if (value == null || value.isBlank() || transcript == null || transcript.isBlank()) {
            return false;
        }
        String v = normalize(value);
        String t = normalize(transcript);
        if (v.isBlank() || t.isBlank()) {
            return false;
        }
        if (v.length() >= Math.max(40, (int) (t.length() * 0.8))) {
            return true;
        }
        return t.contains(v) && v.split(" ").length > 10;
    }

    private String normalize(String s) {
        return s == null ? ""
                : s
                        .toLowerCase(Locale.ROOT)
                        .replace('ё', 'е')
                        .replaceAll("[^a-zа-я0-9 ]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePersonText(String value) {
        String v = normalize(value);
        v = v.replaceAll(
                "\\b(исполнитель|исполниетль|исполнител|описание|дедлайн|срок|приоритет|приоритетность|приоритеность|название|задача|для|кому|назначь|назначить|сделай|сделать|нужно|надо)\\b",
                " ");
        return v.replaceAll("\\s+", " ").trim();
    }

    private String truncateAtActionWords(String value) {
        if (value == null || value.isBlank())
            return "";
        String[] stops = { " сделай ", " сделать ", " нужно ", " надо ", " описание ", " дедлайн ", " срок ",
                " приоритет ", " приоритетность ", " приоритеность ", " название ", " задача " };
        String normalized = " " + value.toLowerCase(Locale.ROOT) + " ";
        int cut = value.length();
        for (String stop : stops) {
            int i = normalized.indexOf(stop);
            if (i >= 0) {
                cut = Math.min(cut, Math.max(0, i - 1));
            }
        }
        return value.substring(0, cut).trim();
    }

    private String sanitizeExtractedValue(String value) {
        if (value == null || value.isBlank())
            return "";
        String cleaned = value
                .replaceAll(
                        "(?i)\\b(?:исполнитель|исполниетль|описание|дедлайн\\s*до|дедлайн|срок\\s*до|срок|приоритет(?:ность)?|приоритеность|название(?:\\s+задачи)?|задача)\\b\\s*[:\\-]?",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private int levenshtein(String a, String b) {
        if (a.equals(b))
            return 0;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++)
            prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(
                        curr[j - 1] + 1,
                        prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private double computeConfidence(VoiceTaskDraftResponse draft) {
        double c = 0.85;
        if (draft.getTitle() == null || draft.getTitle().isBlank())
            c -= 0.2;
        if (draft.getAssigneeId() == null)
            c -= 0.2;
        if (draft.getDueDate() == null || draft.getDueDate().isBlank())
            c -= 0.25;
        if (draft.getAssigneeCandidates() != null && draft.getAssigneeCandidates().size() > 1)
            c -= 0.1;
        return Math.max(0.05, Math.min(0.99, c));
    }
}
