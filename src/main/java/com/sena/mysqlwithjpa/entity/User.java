package com.sena.mysqlwithjpa.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;

import java.time.LocalDateTime;

/**
 * Maps the DBA-owned MySQL `usuario` table one-to-one. Hibernate only validates
 * this schema (`ddl-auto=validate`); it never creates or alters it.
 *
 * Trigger ownership:
 * - `rol` may be null on insert: `@DynamicInsert` omits the column and trigger
 *   `rolDefecto` applies the `USUARIO` default.
 * - `ultima_actualizacion` is fully DB-owned (DB default on insert, trigger
 *   `actualizarFechaUsuario` on update) and therefore read-only here.
 * - `fecha_registro` is set by the application on insert and never updated.
 */
@Entity
@Table(name = "usuario")
@DynamicInsert
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @Column(name = "primer_nombre", nullable = false)
    private String primerNombre;

    @Column(name = "segundo_nombre")
    private String segundoNombre;

    @Column(name = "primer_apellido", nullable = false)
    private String primerApellido;

    @Column(name = "segundo_apellido")
    private String segundoApellido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, columnDefinition = "enum('CC','TI')")
    private TipoDocumento tipoDocumento;

    @Column(name = "documento", nullable = false, unique = true)
    private Long documento;

    @Column(name = "celular")
    private String celular;

    @Column(name = "grupo_formacion")
    private String grupoFormacion;

    @Column(name = "correo_electronico", nullable = false, unique = true)
    private String correoElectronico;

    /** BCrypt hash only; never serialized back to clients. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "contrasena", nullable = false)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, columnDefinition = "enum('ADMIN','USUARIO')")
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_apoyo", columnDefinition = "enum('regular','alimentacion','transporte')")
    private TipoApoyo tipoApoyo;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "ultima_actualizacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime ultimaActualizacion;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPrimerNombre() {
        return primerNombre;
    }

    public void setPrimerNombre(String primerNombre) {
        this.primerNombre = primerNombre;
    }

    public String getSegundoNombre() {
        return segundoNombre;
    }

    public void setSegundoNombre(String segundoNombre) {
        this.segundoNombre = segundoNombre;
    }

    public String getPrimerApellido() {
        return primerApellido;
    }

    public void setPrimerApellido(String primerApellido) {
        this.primerApellido = primerApellido;
    }

    public String getSegundoApellido() {
        return segundoApellido;
    }

    public void setSegundoApellido(String segundoApellido) {
        this.segundoApellido = segundoApellido;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public Long getDocumento() {
        return documento;
    }

    public void setDocumento(Long documento) {
        this.documento = documento;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getGrupoFormacion() {
        return grupoFormacion;
    }

    public void setGrupoFormacion(String grupoFormacion) {
        this.grupoFormacion = grupoFormacion;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = correoElectronico;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public TipoApoyo getTipoApoyo() {
        return tipoApoyo;
    }

    public void setTipoApoyo(TipoApoyo tipoApoyo) {
        this.tipoApoyo = tipoApoyo;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDateTime getUltimaActualizacion() {
        return ultimaActualizacion;
    }

    public void setUltimaActualizacion(LocalDateTime ultimaActualizacion) {
        this.ultimaActualizacion = ultimaActualizacion;
    }
}
