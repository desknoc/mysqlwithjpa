package com.sena.mysqlwithjpa.repository;

import com.sena.mysqlwithjpa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByDocumento(Long documento);

    boolean existsByCorreoElectronico(String correoElectronico);
}
