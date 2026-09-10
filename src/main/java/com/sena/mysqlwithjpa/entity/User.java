package com.sena.mysqlwithjpa.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity // This tells Hibernate to make a table out of this class
public class User {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private @Nullable Integer id;

    // @NotBlank: rechaza null, cadena vacía ("") y cadenas con solo espacios.
    // Es la anotación correcta para campos de texto que sí o sí deben tener contenido.
    @NotBlank(message = "El nombre es obligatorio")

    // @Size: limita la longitud del valor. Evita que el nombre sea demasiado corto
    // (menor al mínimo) o demasiado largo (mayor al máximo).
    @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")

    // @Pattern: valida que el valor cumpla una expresión regular.
    // Aquí solo permite letras (incluye acentos y ñ) y espacios, rechazando números y símbolos.
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$",
            message = "El nombre solo puede contener letras y espacios")
    private String name;

    // @NotNull: solo rechaza null. Permite cadenas vacías, por eso se complementa
    // con @NotBlank más abajo. Su propósito aquí es marcar que el email nunca puede ser null.
    @NotNull(message = "El email no puede ser nulo")

    // @NotBlank: rechaza null, cadena vacía y solo espacios. Junto con @NotNull y @Email,
    // cubre los tres casos: no nulo, no vacío y con formato válido.
    @NotBlank(message = "El email es obligatorio")

    // @Email: valida que el valor tenga un formato de correo electrónico válido.
    @Email(message = "Debe usar el identificador '@'")
    private String email;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}