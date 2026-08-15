document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const logo = document.getElementById('authLogo');
    if (logo) logo.innerHTML = P.logoSvg();

    document.getElementById('registerForm').addEventListener('submit', async event => {
        event.preventDefault();
        const errorDiv = document.getElementById('errorMessage');
        const successDiv = document.getElementById('successMessage');
        const submitBtn = event.target.querySelector('button[type="submit"]');
        errorDiv.classList.add('hidden');
        successDiv.classList.add('hidden');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Creating...';

        try {
            const response = await fetch('/api/v1/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: document.getElementById('username').value,
                    password: document.getElementById('password').value
                })
            });

            if (response.ok) {
                successDiv.classList.remove('hidden');
                window.setTimeout(() => { window.location.href = '/login.html'; }, 1200);
                return;
            }
            const data = await response.json();
            showError(data.message || 'Registration failed.');
        } catch {
            showError('A network error occurred.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Create Account';
        }
    });

    function showError(message) {
        const errorDiv = document.getElementById('errorMessage');
        errorDiv.textContent = message;
        errorDiv.classList.remove('hidden');
    }
});
