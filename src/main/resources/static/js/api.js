// src/main/resources/static/js/api.js
// Центральный модуль для всех запросов к REST API

const API = (() => {
  const BASE = '/api';

  // JWT хранится в localStorage после логина
  const getToken = () => localStorage.getItem('dtm_token');

  const headers = (extra = {}) => ({
    'Content-Type': 'application/json',
    ...(getToken() ? { 'Authorization': `Bearer ${getToken()}` } : {}),
    ...extra
  });

  async function request(method, url, body = null) {
    const options = { method, headers: headers() };
    if (body) options.body = JSON.stringify(body);
    const res = await fetch(BASE + url, options);
    if (res.status === 401) {
      localStorage.removeItem('dtm_token');
      window.location.href = '/login';
      return;
    }
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || 'Ошибка сервера');
    }
    if (res.status === 204) return null;
    return res.json();
  }

  return {
    // ── Auth ──────────────────────────────────────────
    login: (email, password) =>
      request('POST', '/auth/login', { email, password }),

    register: (data) =>
      request('POST', '/auth/register', data),

    // ── Projects ──────────────────────────────────────
    getProjects: () => request('GET', '/projects'),
    createProject: (data) => request('POST', '/projects', data),
    deleteProject: (id) => request('DELETE', `/projects/${id}`),

    // ── Tasks ─────────────────────────────────────────
    getTasksByProject: (projectId) =>
      request('GET', `/tasks/project/${projectId}`),

    createTask: (data) => request('POST', '/tasks', data),
    updateTask: (id, data) => request('PUT', `/tasks/${id}`, data),
    updateStatus: (id, status) =>
      request('PATCH', `/tasks/${id}/status?status=${status}`),
    deleteTask: (id) => request('DELETE', `/tasks/${id}`),
    searchTasks: (projectId, keyword) =>
      request('GET', `/tasks/project/${projectId}/search?keyword=${encodeURIComponent(keyword)}`),

    // ── Subtasks ──────────────────────────────────────
    getSubtasks: (taskId) => request('GET', `/tasks/${taskId}/subtasks`),
    createSubtask: (taskId, data) =>
      request('POST', `/tasks/${taskId}/subtasks`, data),
    updateSubtask: (taskId, id, data) =>
      request('PUT', `/tasks/${taskId}/subtasks/${id}`, data),
    deleteSubtask: (taskId, id) =>
      request('DELETE', `/tasks/${taskId}/subtasks/${id}`),

    // ── Comments ──────────────────────────────────────
    getComments: (taskId) => request('GET', `/tasks/${taskId}/comments`),
    addComment: (taskId, content) =>
      request('POST', `/tasks/${taskId}/comments`, { content }),
    deleteComment: (taskId, id) =>
      request('DELETE', `/tasks/${taskId}/comments/${id}`),

    // ── Attachments ───────────────────────────────────
    getAttachments: (entityType, entityId) =>
      request('GET', `/attachments?entityType=${entityType}&entityId=${entityId}`),

    uploadFile: async (file, entityType, entityId) => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('entityType', entityType);
      formData.append('entityId', entityId);
      const res = await fetch(`${BASE}/attachments/upload`, {
        method: 'POST',
        headers: getToken() ? { 'Authorization': `Bearer ${getToken()}` } : {},
        body: formData
      });
      if (!res.ok) throw new Error('Ошибка загрузки файла');
      return res.json();
    },

    deleteAttachment: (id) => request('DELETE', `/attachments/${id}`),

    // ── Notifications ─────────────────────────────────
    getUnreadNotifs: () => request('GET', '/notifications/unread'),
    getUnreadCount: () => request('GET', '/notifications/count'),
    markRead: (id) => request('PATCH', `/notifications/${id}/read`),
    markAllRead: () => request('PATCH', '/notifications/read-all'),
  };
})();