// src/main/resources/static/js/kanban.js

const projectId = document.getElementById('pageData').dataset.projectId;
let draggedTaskId = null;

// ── Drag & Drop ──────────────────────────────────────

function dragStart(event, taskId) {
  draggedTaskId = taskId;
  event.target.classList.add('dragging');
  event.dataTransfer.effectAllowed = 'move';
}

async function onDrop(event, newStatus) {
  event.preventDefault();
  const col = event.currentTarget;
  col.classList.remove('drag-over');

  if (!draggedTaskId) return;

  try {
    await API.updateStatus(draggedTaskId, newStatus);

    // Переносим карточку DOM без перезагрузки
    const card = document.getElementById('task-' + draggedTaskId);
    if (card) {
      card.classList.remove('dragging');
      col.appendChild(card);
    }

    // Обновляем счётчики
    updateCounters();
  } catch (e) {
    alert('Ошибка смены статуса: ' + e.message);
  }
  draggedTaskId = null;
}

function updateCounters() {
  ['NEW', 'IN_PROGRESS', 'REVIEW', 'DONE'].forEach(status => {
    const col = document.getElementById('col-' + status);
    const header = col.closest('.kanban-col').querySelector('.col-count');
    header.textContent = col.querySelectorAll('.task-card').length;
  });
}

// ── Создание задачи ───────────────────────────────────

function openCreateTask() {
  document.getElementById('createTaskModal').classList.add('active');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

async function createTask() {
  const title    = document.getElementById('tTitle').value.trim();
  const desc     = document.getElementById('tDesc').value.trim();
  const priority = document.getElementById('tPriority').value;
  const dueDate  = document.getElementById('tDueDate').value;

  if (!title) { alert('Введите название задачи'); return; }

  const user = JSON.parse(localStorage.getItem('dtm_user') || '{}');

  try {
    await API.createTask({
      title,
      description: desc,
      projectId: parseInt(projectId),
      priority,
      dueDate: dueDate || null,
      assigneeIds: [] // можно расширить
    });
    window.location.reload();
  } catch (e) {
    alert('Ошибка: ' + e.message);
  }
}

// ── Поиск ─────────────────────────────────────────────

let searchTimeout;
async function searchTasks(keyword) {
  clearTimeout(searchTimeout);
  searchTimeout = setTimeout(async () => {
    const allCards = document.querySelectorAll('.task-card');
    if (!keyword.trim()) {
      allCards.forEach(c => c.style.display = '');
      return;
    }
    const kw = keyword.toLowerCase();
    allCards.forEach(card => {
      const title = card.querySelector('.task-title').textContent.toLowerCase();
      card.style.display = title.includes(kw) ? '' : 'none';
    });
  }, 300);
}

// ── Фильтр по приоритету ──────────────────────────────

function filterByPriority(priority) {
  const allCards = document.querySelectorAll('.task-card');
  allCards.forEach(card => {
    if (!priority) {
      card.style.display = '';
      return;
    }
    const badge = card.querySelector('.priority');
    card.style.display = (badge && badge.textContent.trim() === priority) ? '' : 'none';
  });
}

// ── Уведомления ───────────────────────────────────────

function toggleNotif() {
  const dd = document.getElementById('notifDropdown');
  dd?.classList.toggle('active');
}

document.addEventListener('click', (e) => {
  const dd = document.getElementById('notifDropdown');
  if (dd && !e.target.closest('.notif-btn') && !e.target.closest('#notifDropdown')) {
    dd.classList.remove('active');
  }
});

