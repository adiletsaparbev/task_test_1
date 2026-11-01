package com.example.demo.config;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Custom Logback layout для маскировки чувствительных данных в логах.
 * Заменяет пароли, токены, API ключи на "***MASKED***".
 */
public class MaskingPatternLayout extends PatternLayout {

    private final List<Pattern> maskPatterns = new ArrayList<>();
    private static final String MASKED_VALUE = "***MASKED***";

    public void addMaskPattern(String pattern) {
        maskPatterns.add(Pattern.compile(pattern, Pattern.MULTILINE));
    }

    public void setMaskPattern(String pattern) {
        addMaskPattern(pattern);
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        return maskMessage(message);
    }

    private String maskMessage(String message) {
        if (message == null || maskPatterns.isEmpty()) {
            return message;
        }

        String maskedMessage = message;
        for (Pattern pattern : maskPatterns) {
            Matcher matcher = pattern.matcher(maskedMessage);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String replacement;
                if (matcher.groupCount() > 0) {
                    // Заменяем только захваченную группу (значение)
                    String prefix = maskedMessage.substring(matcher.start(), matcher.start(1));
                    String suffix = maskedMessage.substring(matcher.end(1), matcher.end());
                    replacement = Matcher.quoteReplacement(prefix + MASKED_VALUE + suffix);
                } else {
                    // Заменяем всё совпадение
                    replacement = MASKED_VALUE;
                }
                matcher.appendReplacement(sb, replacement);
            }
            matcher.appendTail(sb);
            maskedMessage = sb.toString();
        }

        return maskedMessage;
    }
}
