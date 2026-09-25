package com.sena.mysqlwithjpa.repository;

import com.sena.mysqlwithjpa.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByDocumento(Long documento);

    boolean existsByCorreoElectronico(String correoElectronico);

    // Derived queries (spec user-search-pagination): parameterized by
    // construction — criteria are bound parameters, never string-interpolated.
    // A null `documento` on the OR branch simply never matches (SQL `= null`
    // is unknown), which is exactly the controller contract for non-numeric
    // terms.
    Page<User> findByPrimerNombreAndDocumento(String primerNombre, Long documento, Pageable pageable);

    Page<User> findByPrimerNombreOrPrimerApellidoOrDocumento(
            String primerNombre, String primerApellido, Long documento, Pageable pageable);
}
