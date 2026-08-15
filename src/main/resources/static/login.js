document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const logo = document.getElementById('authLogo');
    if (logo) logo.innerHTML = P.logoSvg();

    document.getElementById('loginForm').addEventListener('submit', async event => {
        event.preventDefault();
        const errorDiv = document.getElementById('errorMessage');
        const submitBtn = event.target.querySelector('button[type="submit"]');
        errorDiv.classList.add('hidden');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Signing in...';

        try {
            const response = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: document.getElementById('username').value,
                    password: document.getElementById('password').value
                })
            });

            if (response.ok) {
                window.location.href = '/';
                return;
            }
            const data = await response.json();
            showError(data.message || 'Invalid username or password.');
        } catch {
            showError('A network error occurred.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In';
        }
    });

    function showError(message) {
        const errorDiv = document.getElementById('errorMessage');
        errorDiv.textContent = message;
        errorDiv.classList.remove('hidden');
    }
});
