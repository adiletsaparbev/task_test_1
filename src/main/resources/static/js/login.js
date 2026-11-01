// src/main/resources/static/js/login.js

async function doLogin() {
  const email    = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;
  const errEl    = document.getElementById('errorMsg');

  if (!email || !password) {
    errEl.textContent = 'Заполните email и пароль';
    errEl.style.display = 'block';
    return;
  }

  try {
    const data = await API.login(email, password);
    // Сохраняем JWT токен
    localStorage.setItem('dtm_token', data.token);
    localStorage.setItem('dtm_user', JSON.stringify({
      email: data.email, role: data.role, fullName: data.fullName
    }));
    window.location.href = '/projects';
  } catch (e) {
    errEl.textContent = 'Неверный email или пароль';
    errEl.style.display = 'block';
  }
}

// Enter — логин
document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') doLogin();
});