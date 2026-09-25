package com.sena.mysqlwithjpa.dto;

import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound payload for user creation and full update.
 *
 * Required on every request: primerNombre, primerApellido, tipoDocumento,
 * documento, celular, correoElectronico. {@code contrasena} is required only
 * on creation ({@link OnCreate} group); on update it may be null, in which
 * case the stored hash is left untouched (service-enforced length rules apply
 * whenever a value is present). Optional: segundoNombre, segundoApellido,
 * grupoFormacion, rol, tipoApoyo. Trigger-/DB-owned fields (fechaRegistro,
 * ultimaActualizacion) are not part of the contract at all — clients cannot
 * supply them, so they are ignored by construction.
 */
public record UserRequest(
        @NotBlank String primerNombre,
        String segundoNombre,
        @NotBlank String primerApellido,
        String segundoApellido,
        @NotNull TipoDocumento tipoDocumento,
        @NotNull Long documento,
        @NotBlank String celular,
        String grupoFormacion,
        @NotBlank String correoElectronico,
        @NotBlank(groups = OnCreate.class) String contrasena,
        Rol rol,
        TipoApoyo tipoApoyo) {

    /**
     * Bean-validation group activated by the controller on POST (creation).
     * Extends {@link Default} so the non-grouped constraints (the required
     * fields) keep validating when this group is selected.
     */
    public interface OnCreate extends jakarta.validation.groups.Default {
    }
}
