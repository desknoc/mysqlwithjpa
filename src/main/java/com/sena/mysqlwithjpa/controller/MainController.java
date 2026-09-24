package com.sena.mysqlwithjpa.controller;

import com.sena.mysqlwithjpa.entity.User;
import com.sena.mysqlwithjpa.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller // This means that this class is a Controller
@RequestMapping(path="/demo") // This means URL's start with /demo (after Application path)
public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final UserRepository userRepository;

    @Autowired // This means to get the bean called userRepository
    public MainController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Legacy demo endpoint: `User` no longer carries generic name/email columns;
    // writes are superseded by POST /api/users (rest-api-redesign Phase 4).
    @PostMapping(path="/add")
    public @ResponseBody String addNewUser(@RequestParam String name
            , @RequestParam String email) {
        log.warn("Solicitud rechazada en endpoint legado /demo/add: name={}", name);
        throw new UnsupportedOperationException(
                "Legacy /demo/add was removed with the usuario entity migration; use POST /api/users");
    }

    @GetMapping(path="/all")
    public @ResponseBody Iterable<User> getAllUsers() {

        log.info("Solicitando la lista de todos los usuarios registrados");

        // This returns a JSON or XML with the users
        return userRepository.findAll();
    }
}
