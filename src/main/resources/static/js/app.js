const form = document.getElementById('user-form');
const messageBox = document.getElementById('message');
const tableBody = document.getElementById('user-table-body');

async function loadUsers() {
    try {
        const response = await fetch('/demo/all');
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        const users = await response.json();
        renderUsers(users);
    } catch (error) {
        renderEmpty('No se pudo cargar la lista. ¿Está corriendo la app?');
    }
}

function renderUsers(users) {
    tableBody.innerHTML = '';

    if (users.length === 0) {
        renderEmpty('No hay usuarios todavía. ¡Agregá el primero!');
        return;
    }

    users.forEach(user => {
        const row = document.createElement('tr');

        const idCell = document.createElement('td');
        idCell.textContent = user.id;

        const nameCell = document.createElement('td');
        nameCell.textContent = user.name;

        const emailCell = document.createElement('td');
        emailCell.textContent = user.email;

        row.appendChild(idCell);
        row.appendChild(nameCell);
        row.appendChild(emailCell);
        tableBody.appendChild(row);
    });
}

function renderEmpty(text) {
    tableBody.innerHTML = '<tr><td colspan="3" class="empty">' + text + '</td></tr>';
}

function showMessage(text, type) {
    messageBox.textContent = text;
    messageBox.className = 'message visible ' + type;
    setTimeout(() => {
        messageBox.className = 'message';
    }, 5000);
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();

    const url = '/demo/add?name=' + encodeURIComponent(name) + '&email=' + encodeURIComponent(email);

    try {
        const response = await fetch(url, { method: 'POST' });

        if (response.ok) {
            showMessage('Usuario agregado correctamente.', 'success');
            form.reset();
            loadUsers();
        } else {
            const errorData = await response.json();
            showMessage(errorData.message || 'No se pudo agregar el usuario.', 'error');
        }
    } catch (error) {
        showMessage('Error de conexión. ¿Está corriendo la app?', 'error');
    }
});

loadUsers();