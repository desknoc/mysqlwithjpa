package com.sena.mysqlwithjpa.dto;

import com.sena.mysqlwithjpa.entity.Rol;
import com.sena.mysqlwithjpa.entity.TipoApoyo;
import com.sena.mysqlwithjpa.entity.TipoDocumento;
import com.sena.mysqlwithjpa.entity.User;

import java.time.LocalDateTime;

/**
 * Outbound user representation. NEVER contains the password: the field simply
 * does not exist in this record, so neither the plaintext nor the BCrypt hash
 * can leak through serialization.
 */
public record UserResponse(
        Integer id,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        TipoDocumento tipoDocumento,
        Long documento,
        String celular,
        String grupoFormacion,
        String correoElectronico,
        Rol rol,
        TipoApoyo tipoApoyo,
        LocalDateTime fechaRegistro,
        LocalDateTime ultimaActualizacion) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getPrimerNombre(),
                user.getSegundoNombre(),
                user.getPrimerApellido(),
                user.getSegundoApellido(),
                user.getTipoDocumento(),
                user.getDocumento(),
                user.getCelular(),
                user.getGrupoFormacion(),
                user.getCorreoElectronico(),
                user.getRol(),
                user.getTipoApoyo(),
                user.getFechaRegistro(),
                user.getUltimaActualizacion());
    }
}
