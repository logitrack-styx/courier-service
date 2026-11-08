package com.courier.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.courier.service.client.UsuarioRolVerifier;
import com.courier.service.enums.EstadoCourier;
import com.courier.service.exceptions.ServicioExternoException;
import com.courier.service.exceptions.UsuarioInvalidoException;
import com.courier.service.model.Courier;
import com.courier.service.repository.CourierRepository;

/**
 * Pruebas de integración de courier-service: API completa + Spring Security con JWT RS256
 * reales (firmados en el test) + H2. security-service se reemplaza por un mock.
 * @author TheMiggaCool
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourierApiTest {
    @Test
    void documentsBearerAndBoundsListQueries() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/couriers']").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        for (int i = 0; i < 25; i++) guardar(UUID.randomUUID(), "Courier " + i, EstadoCourier.ACTIVO);
        mvc.perform(conAdmin(get("/couriers").param("size", "10")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Total-Count", "25"));
        mvc.perform(conAdmin(get("/couriers").param("page", "99")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(conAdmin(get("/couriers").param("size", "101"))).andExpect(status().isBadRequest());
        mvc.perform(conAdmin(get("/couriers").param("page", "-1"))).andExpect(status().isBadRequest());
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location",
                JwtTest::publicKeyLocation);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    CourierRepository repository;
    @MockitoBean
    UsuarioRolVerifier usuarioRolVerifier;

    final UUID adminId = UUID.randomUUID();
    String admin;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
        admin = bearer(JwtTest.token(adminId, "ADMIN"));
    }

    // ---------------------------------------------------------------- helpers

    static String bearer(String jwt) {
        return "Bearer " + jwt;
    }

    Courier guardar(UUID id, String nombre, EstadoCourier estado) {
        return repository.save(Courier.builder().id(id).nombre(nombre).tipoVehiculo("Moto").estado(estado).build());
    }

    MockHttpServletRequestBuilder conAdmin(MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, admin);
    }

    static String json(String... paresClaveValor) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(paresClaveValor[i]).append("\":").append(paresClaveValor[i + 1]);
        }
        return sb.append('}').toString();
    }

    static String str(String s) {
        return "\"" + s + "\"";
    }

    // ---------------------------------------------------------------- alta

    @Nested
    @DisplayName("POST /couriers (alta)")
    class Alta {

        @Test
        @DisplayName("crea el courier con id = userId, estado ACTIVO y verifica el rol")
        void altaOk() throws Exception {
            UUID userId = UUID.randomUUID();
            mvc.perform(conAdmin(post("/couriers")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("userId", str(userId.toString()), "nombre", str("Carlos Gómez"),
                                    "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.estado").value("ACTIVO"))
                    .andExpect(jsonPath("$.nombre").value("Carlos Gómez"));
            verify(usuarioRolVerifier).verificarRepartidor(userId);
        }

        @Test
        @DisplayName("userId repetido -> 409")
        void altaDuplicada() throws Exception {
            UUID userId = UUID.randomUUID();
            guardar(userId, "Ya existe", EstadoCourier.ACTIVO);
            mvc.perform(conAdmin(post("/couriers")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("userId", str(userId.toString()), "nombre", str("Otro"), "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
            verifyNoInteractions(usuarioRolVerifier);
        }

        @Test
        @DisplayName("el userId no es REPARTIDOR -> 400 y no se persiste nada")
        void usuarioSinRol() throws Exception {
            UUID userId = UUID.randomUUID();
            doThrow(new UsuarioInvalidoException("El usuario no tiene el rol REPARTIDOR"))
                    .when(usuarioRolVerifier).verificarRepartidor(userId);
            mvc.perform(conAdmin(post("/couriers")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("userId", str(userId.toString()), "nombre", str("Carlos"), "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("El usuario no tiene el rol REPARTIDOR"));
            org.junit.jupiter.api.Assertions.assertFalse(repository.existsById(userId));
        }

        @Test
        @DisplayName("security-service caído -> 503")
        void securityCaido() throws Exception {
            UUID userId = UUID.randomUUID();
            doThrow(new ServicioExternoException("No se pudo consultar a security-service"))
                    .when(usuarioRolVerifier).verificarRepartidor(any());
            mvc.perform(conAdmin(post("/couriers")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("userId", str(userId.toString()), "nombre", str("Carlos"), "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("datos inválidos -> 400 con el formato de error del contrato")
        void datosInvalidos() throws Exception {
            mvc.perform(conAdmin(post("/couriers")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("nombre", str(" "), "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    // ---------------------------------------------------------------- consulta

    @Nested
    @DisplayName("GET /couriers")
    class Consulta {

        @Test
        @DisplayName("lista todos y filtra por estado")
        void listaYFiltro() throws Exception {
            guardar(UUID.randomUUID(), "Ana", EstadoCourier.ACTIVO);
            guardar(UUID.randomUUID(), "Beto", EstadoCourier.INACTIVO);
            mvc.perform(conAdmin(get("/couriers"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
            mvc.perform(conAdmin(get("/couriers")).param("estado", "INACTIVO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nombre").value("Beto"));
        }

        @Test
        @DisplayName("estado inválido -> 400")
        void estadoInvalido() throws Exception {
            mvc.perform(conAdmin(get("/couriers")).param("estado", "VOLANDO")).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("por id: 200 si existe, 404 con formato de error si no")
        void porId() throws Exception {
            UUID id = UUID.randomUUID();
            guardar(id, "Ana", EstadoCourier.ACTIVO);
            mvc.perform(conAdmin(get("/couriers/" + id))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));

            mvc.perform(conAdmin(get("/couriers/" + UUID.randomUUID())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        @DisplayName("id con formato inválido -> 400")
        void idInvalido() throws Exception {
            mvc.perform(conAdmin(get("/couriers/no-es-uuid"))).andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------- actualización, estado y disponibilidad

    @Nested
    @DisplayName("PUT / PATCH / disponibilidad")
    class Modificacion {

        @Test
        @DisplayName("PUT actualiza nombre y vehículo; 404 si no existe")
        void actualizar() throws Exception {
            UUID id = UUID.randomUUID();
            guardar(id, "Ana", EstadoCourier.ACTIVO);
            mvc.perform(conAdmin(put("/couriers/" + id)).contentType(MediaType.APPLICATION_JSON)
                            .content(json("nombre", str("Ana María"), "tipoVehiculo", str("Bicicleta"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Ana María"))
                    .andExpect(jsonPath("$.tipoVehiculo").value("Bicicleta"));

            mvc.perform(conAdmin(put("/couriers/" + UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                            .content(json("nombre", str("X Y"), "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PATCH /estado cambia el estado; valor inválido -> 400; inexistente -> 404")
        void cambiarEstado() throws Exception {
            UUID id = UUID.randomUUID();
            guardar(id, "Ana", EstadoCourier.ACTIVO);
            mvc.perform(conAdmin(patch("/couriers/" + id + "/estado")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("estado", str("INACTIVO"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("INACTIVO"));

            mvc.perform(conAdmin(patch("/couriers/" + id + "/estado")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("estado", str("VOLANDO"))))
                    .andExpect(status().isBadRequest());

            mvc.perform(conAdmin(patch("/couriers/" + UUID.randomUUID() + "/estado"))
                            .contentType(MediaType.APPLICATION_JSON).content(json("estado", str("ACTIVO"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /disponibilidad: ACTIVO disponible, INACTIVO no, inexistente 404")
        void disponibilidad() throws Exception {
            UUID activo = UUID.randomUUID();
            UUID inactivo = UUID.randomUUID();
            guardar(activo, "Ana", EstadoCourier.ACTIVO);
            guardar(inactivo, "Beto", EstadoCourier.INACTIVO);

            mvc.perform(conAdmin(get("/couriers/" + activo + "/disponibilidad")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(activo.toString()))
                    .andExpect(jsonPath("$.disponible").value(true));
            mvc.perform(conAdmin(get("/couriers/" + inactivo + "/disponibilidad")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disponible").value(false))
                    .andExpect(jsonPath("$.estado").value("INACTIVO"));
            mvc.perform(conAdmin(get("/couriers/" + UUID.randomUUID() + "/disponibilidad")))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- seguridad

    @Nested
    @DisplayName("Seguridad JWT RS256")
    class Seguridad {

        @Test
        @DisplayName("sin token -> 401 con formato de error")
        void sinToken() throws Exception {
            mvc.perform(get("/couriers"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }

        @Test
        @DisplayName("token expirado -> 401")
        void tokenExpirado() throws Exception {
            mvc.perform(get("/couriers").header(HttpHeaders.AUTHORIZATION,
                            bearer(JwtTest.tokenExpirado(adminId, "ADMIN"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("firma inválida (otra clave) -> 401")
        void firmaInvalida() throws Exception {
            mvc.perform(get("/couriers").header(HttpHeaders.AUTHORIZATION,
                            bearer(JwtTest.tokenConFirmaInvalida(adminId, "ADMIN"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("token manipulado -> 401")
        void tokenManipulado() throws Exception {
            mvc.perform(get("/couriers").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rol CLIENTE no puede listar ni crear -> 403 con formato de error")
        void rolInsuficiente() throws Exception {
            String cliente = bearer(JwtTest.token(UUID.randomUUID(), "CLIENTE"));
            mvc.perform(get("/couriers").header(HttpHeaders.AUTHORIZATION, cliente))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"));
            mvc.perform(post("/couriers").header(HttpHeaders.AUTHORIZATION, cliente)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("userId", str(UUID.randomUUID().toString()), "nombre", str("Ana"), "tipoVehiculo", str("Moto"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("REPARTIDOR accede a su propio perfil pero no al de otro (pertenencia del recurso)")
        void pertenenciaDelRecurso() throws Exception {
            UUID yo = UUID.randomUUID();
            UUID otro = UUID.randomUUID();
            guardar(yo, "Yo", EstadoCourier.ACTIVO);
            guardar(otro, "Otro", EstadoCourier.ACTIVO);
            String miToken = bearer(JwtTest.token(yo, "REPARTIDOR"));

            mvc.perform(get("/couriers/" + yo).header(HttpHeaders.AUTHORIZATION, miToken))
                    .andExpect(status().isOk());
            mvc.perform(get("/couriers/" + yo + "/disponibilidad").header(HttpHeaders.AUTHORIZATION, miToken))
                    .andExpect(status().isOk());
            mvc.perform(get("/couriers/" + otro).header(HttpHeaders.AUTHORIZATION, miToken))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/couriers/" + yo + "/estado").header(HttpHeaders.AUTHORIZATION, miToken)
                            .contentType(MediaType.APPLICATION_JSON).content(json("estado", str("INACTIVO"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/actuator/health es público")
        void healthPublico() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
