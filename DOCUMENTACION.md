# Introducción

Para hacer las excepciones de nuestro programa debemos comenzar por definir las características de las funciones que queremos llevar a cabo.

- **Objeto de errores**: Aquí transportaremos cualquier fallo del crud de usuarios o de cualquier otro que hagamos, así solo se parsean todos los errores de una manera en lugar de muchas.

# Construcción

## Clase: ApiError

La usaremos para poder obtener los errores globales de la aplicación con sus propiedades:

- **timestamp** (LocalDateTime): para saber cuándo se obtuvo el error.
- **status** (int): el código de estado HTTP de la respuesta (ej. 400, 404, 500).
- **error** (String): el nombre corto del error, según el estándar HTTP (ej. "Bad Request").
- **message** (String): el mensaje de error que ve el usuario, con el detalle de qué falló.
- **path** (String): la URL (ruta) donde ocurrió el error.

Cada propiedad tiene sus **getters y setters** para poder leer y modificar los valores.

## Clase: ExceptionController

Es el **controlador de manejo de excepciones** global de la aplicación. Se encarga de atrapar los errores que ocurran en cualquier controller y devolverlos en el formato de `ApiError`.

- Anotada con **`@RestControllerAdvice`**: le dice a Spring que esta clase atiende los errores de TODA la aplicación, sin importar en qué controller ocurran.
- **`@ExceptionHandler`**: le dice a Spring qué método debe atender cada tipo de excepción. Spring siempre elige el manejador más específico para cada error.
- Los métodos reciben la excepción y el **`HttpServletRequest`** (para conocer la URL de la petición).
- Los métodos **devuelven** un **`ResponseEntity<ApiError>`**: la respuesta HTTP completa con el código de estado y el objeto `ApiError` como cuerpo (JSON).

### Errores manejados

- **400 — Bad Request**: se activa cuando el cliente hace una petición sin los parámetros requeridos. Método `manejarParametroFaltante`, anotado con `@ExceptionHandler(MissingServletRequestParameterException.class)`.
- **404 — Not Found**: se activa cuando se busca un recurso que no existe en la base de datos (ej. un usuario con un id inexistente). Método `manejarElementoNoEncontrado`, anotado con `@ExceptionHandler(NoSuchElementException.class)`.
- **500 — Internal Server Error**: es la red de seguridad global. Se activa ante cualquier excepción inesperada que ningún otro manejador atienda. Método `manejarErrorInesperado`, anotado con `@ExceptionHandler(Exception.class)`. No expone detalles técnicos internos al cliente, solo un mensaje genérico.

**Flujo de funcionamiento:**

1. El usuario hace una petición a la API.
2. Ocurre una excepción durante el procesamiento (falta un parámetro, no se encuentra un recurso, error inesperado).
3. El `@RestControllerAdvice` detecta la excepción y llama al método indicado por el `@ExceptionHandler` más específico.
4. El método llena los 5 campos del `ApiError`.
5. Se devuelve la respuesta HTTP con el código correspondiente y el JSON del error.

**Ejemplo de respuesta JSON (400):**

```json
{
  "timestamp": "2026-08-14T14:46:32.135",
  "status": 400,
  "error": "Bad Request",
  "message": "Required request parameter 'name' for method parameter type String is not present",
  "path": "/demo/add"
}
```

## Frontend (extra)

La aplicación incluye un frontend servido por Spring Boot desde `src/main/resources/static/`, accesible en `http://localhost:8080/`:

- **`index.html`**: la página con un formulario para agregar usuarios y una tabla para listarlos.
- **`css/styles.css`**: todos los estilos (sin estilos inline en el HTML).
- **`js/app.js`**: la lógica que consume la API (`POST /demo/add` y `GET /demo/all`) y muestra los mensajes de error del backend.
