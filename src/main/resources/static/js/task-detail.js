// src/main/resources/static/js/task-detail.js

const taskId    = document.getElementById('pageData').dataset.taskId;
const projectId = document.getElementById('pageData').dataset.projectId;

// ── При загрузке страницы ─────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  loadSubtasks();
  loadComments();
  loadAttachments();
});

// ── Статус ────────────────────────────────────────────
async function changeStatus(newStatus) {
  try {
    await API.updateStatus(taskId, newStatus);
    // Обновляем бейдж без перезагрузки
    const badge = document.querySelector('.status');
    badge.className = 'status status-' + newStatus;
    badge.textContent = newStatus;
  } catch (e) {
    alert('Ошибка: ' + e.message);
  }
}

// ── Удалить задачу ────────────────────────────────────
async function deleteTask() {
  if (!confirm('Удалить задачу?')) return;
  try {
    await API.deleteTask(taskId);
    window.location.href = `/projects/${projectId}/board`;
  } catch (e) {
    alert('Ошибка: ' + e.message);
  }
}

// ── Подзадачи ─────────────────────────────────────────
async function loadSubtasks() {
  try {
    const subtasks = await API.getSubtasks(taskId);
    const list = document.getElementById('subtaskList');
    const total = subtasks.length;
    const done  = subtasks.filter(s => s.status === 'DONE').length;

    document.getElementById('subtaskProgress').textContent =
      total ? `${done}/${total}` : '';
    document.getElementById('progressFill').style.width =
      total ? `${(done / total) * 100}%` : '0%';

    list.innerHTML = subtasks.map(s => `
      <div class="subtask-item">
        <div class="subtask-check ${s.status === 'DONE' ? 'done' : ''}"
             onclick="toggleSubtask(${s.id}, '${s.status}')"></div>
        <span class="subtask-title ${s.status === 'DONE' ? 'done' : ''}">${s.title}</span>
        <span style="color:var(--text-dim);font-size:.78rem;margin-left:auto">
          ${s.assignee ? s.assignee.fullName : '—'}
        </span>
        <button onclick="deleteSubtask(${s.id})"
                style="background:none;border:none;color:var(--text-dim);cursor:pointer;padding:2px 6px;border-radius:4px"
                title="Удалить">×</button>
      </div>
    `).join('') || '<p style="color:var(--text-dim);font-size:.85rem">Подзадач нет</p>';
  } catch (e) {}
}

async function toggleSubtask(id, currentStatus) {
  const newStatus = currentStatus === 'DONE' ? 'IN_PROGRESS' : 'DONE';
  try {
    await API.updateSubtask(taskId, id, { status: newStatus });
    loadSubtasks();
  } catch (e) {}
}

async function deleteSubtask(id) {
  try {
    await API.deleteSubtask(taskId, id);
    loadSubtasks();
  } catch (e) {}
}

function openAddSubtask() {
  const title = prompt('Название подзадачи:');
  if (!title) return;
  API.createSubtask(taskId, { title, status: 'NEW' })
     .then(() => loadSubtasks())
     .catch(e => alert(e.message));
}

// ── Комментарии ───────────────────────────────────────
async function loadComments() {
  try {
    const comments = await API.getComments(taskId);
    const list = document.getElementById('commentList');
    list.innerHTML = comments.map(c => `
      <div class="comment-item">
        <div class="comment-avatar">${c.author.fullName.charAt(0)}</div>
        <div class="comment-body">
          <div class="comment-meta">
            <span class="comment-author">${c.author.fullName}</span>
            <span class="comment-time">${new Date(c.createdAt).toLocaleString('ru')}</span>
          </div>
          <p class="comment-text">${escapeHtml(c.content)}</p>
        </div>
      </div>
    `).join('') || '<p style="color:var(--text-dim);font-size:.85rem;margin-bottom:12px">Комментариев нет</p>';
  } catch (e) {}
}

async function sendComment() {
  const input   = document.getElementById('commentInput');
  const content = input.value.trim();
  if (!content) return;
  try {
    await API.addComment(taskId, content);
    input.value = '';
    loadComments();
  } catch (e) {
    alert('Ошибка: ' + e.message);
  }
}

// ── Файлы ─────────────────────────────────────────────
async function loadAttachments() {
  try {
    const files = await API.getAttachments('TASK', taskId);
    const list  = document.getElementById('attachmentList');
    list.innerHTML = files.map(f => `
      <div class="attach-item">
        <span class="attach-icon">${fileIcon(f.mimeType)}</span>
        <span class="attach-name">${f.fileName}</span>
        <span class="attach-size">${formatBytes(f.fileSize)}</span>
        <a href="/api/attachments/${f.id}/download"
           style="color:var(--accent);font-size:.78rem;text-decoration:none;margin-left:6px">
          ↓
        </a>
        <button onclick="deleteAttachment(${f.id})"
                style="background:none;border:none;color:var(--text-dim);cursor:pointer;padding:2px 6px">×</button>
      </div>
    `).join('');
  } catch (e) {}
}

async function uploadFile(file) {
  if (!file) return;
  try {
    await API.uploadFile(file, 'TASK', taskId);
    loadAttachments();
  } catch (e) {
    alert('Ошибка загрузки: ' + e.message);
  }
}

function handleFileDrop(event) {
  event.preventDefault();
  const file = event.dataTransfer.files[0];
  if (file) uploadFile(file);
}

async function deleteAttachment(id) {
  if (!confirm('Удалить файл?')) return;
  try {
    await API.deleteAttachment(id);
    loadAttachments();
  } catch (e) {}
}

// ── Уведомления ───────────────────────────────────────
function toggleNotif() {
  document.getElementById('notifDropdown')?.classList.toggle('active');
}

// ── Утилиты ───────────────────────────────────────────
function escapeHtml(str) {
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function formatBytes(bytes) {
  if (!bytes) return '';
  if (bytes < 1024) return bytes + ' Б';
  if (bytes < 1048576) return (bytes/1024).toFixed(1) + ' КБ';
  return (bytes/1048576).toFixed(1) + ' МБ';
}

function fileIcon(mime) {
  if (!mime) return '📄';
  if (mime.startsWith('image/'))      return '🖼️';
  if (mime.includes('pdf'))           return '📕';
  if (mime.includes('word'))          return '📝';
  if (mime.includes('excel') || mime.includes('spreadsheet')) return '📊';
  if (mime.includes('zip') || mime.includes('rar'))           return '🗜️';
  return '📄';
}