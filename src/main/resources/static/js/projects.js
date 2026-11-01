// src/main/resources/static/js/projects.js

function openCreateProject() {
  document.getElementById('createProjectModal').classList.add('active');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

async function createProject() {
  const name = document.getElementById('pName').value.trim();
  const desc = document.getElementById('pDesc').value.trim();

  if (!name) {
    alert('Введите название проекта');
    return;
  }

  try {
    await API.createProject({ name, description: desc });
    window.location.reload();
  } catch (e) {
    alert('Ошибка: ' + e.message);
  }
}

// ── Уведомления ──────────────────────────────────────
function toggleNotif() {
  const dd = document.getElementById('notifDropdown');
  dd.classList.toggle('active');
  if (dd.classList.contains('active')) loadNotifs();
}

async function loadNotifs() {
  try {
    const notifs = await API.getUnreadNotifs();
    const list = document.getElementById('notifList');
    if (!notifs.length) {
      list.innerHTML = '<p style="padding:16px;color:var(--text-dim);text-align:center;font-size:.85rem">Нет уведомлений</p>';
      return;
    }
    list.innerHTML = notifs.map(n => `
      <div class="notif-item ${!n.read ? 'unread' : ''}" onclick="readNotif(${n.id})">
        <p class="notif-msg">${n.message}</p>
        <p class="notif-time">${new Date(n.createdAt).toLocaleString('ru')}</p>
      </div>
    `).join('');
  } catch(e) {}
}

async function readNotif(id) {
  await API.markRead(id);
  loadNotifs();
}

async function markAllRead() {
  await API.markAllRead();
  document.querySelector('.notif-badge').style.display = 'none';
  loadNotifs();
}

// Закрыть дропдаун при клике снаружи
document.addEventListener('click', (e) => {
  const dd = document.getElementById('notifDropdown');
  if (!e.target.closest('.notif-btn') && !e.target.closest('#notifDropdown')) {
    dd.classList.remove('active');
  }
});