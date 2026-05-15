package com.parkease.vehicle.service;

import com.parkease.vehicle.dto.request.VehicleRequestDTO;
import com.parkease.vehicle.dto.response.VehicleResponseDTO;
import com.parkease.vehicle.entity.Vehicle;
import com.parkease.vehicle.entity.VehicleType;
import com.parkease.vehicle.exception.ResourceNotFoundException;
import com.parkease.vehicle.mapper.VehicleMapper;
import com.parkease.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository repo;

    @Mock
    private VehicleMapper mapper;

    @InjectMocks
    private VehicleServiceImpl service;

    private Vehicle vehicle;
    private VehicleResponseDTO responseDTO;
    private VehicleRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        vehicle = Vehicle.builder()
                .vehicleId(1L)
                .ownerEmail("aryan@test.com")
                .licensePlate("MP04AB1234")
                .make("Hyundai")
                .model("i20")
                .color("White")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .isEV(false)
                .isActive(true)
                .build();

        responseDTO = new VehicleResponseDTO();

        requestDTO = new VehicleRequestDTO();
        requestDTO.setLicensePlate("mp04ab1234");
        requestDTO.setMake("Hyundai");
        requestDTO.setModel("i20");
        requestDTO.setColor("White");
        requestDTO.setVehicleType(VehicleType.FOUR_WHEELER);
        requestDTO.setEV(false);
    }

    @Test
    void shouldRegisterVehicleSuccessfully() {
        when(repo.existsByOwnerEmailAndLicensePlate(
                "aryan@test.com", "MP04AB1234"
        )).thenReturn(false);

        when(mapper.toEntity(any(VehicleRequestDTO.class), eq("aryan@test.com")))
                .thenReturn(vehicle);
        when(repo.save(vehicle)).thenReturn(vehicle);
        when(mapper.toDTO(vehicle)).thenReturn(responseDTO);

        VehicleResponseDTO result =
                service.registerVehicle(requestDTO, "aryan@test.com");

        assertNotNull(result);
        verify(repo).save(vehicle);
        assertEquals("MP04AB1234", requestDTO.getLicensePlate());
    }

    @Test
    void shouldThrowWhenDuplicateVehiclePlateExists() {
        when(repo.existsByOwnerEmailAndLicensePlate(
                "aryan@test.com", "MP04AB1234"
        )).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerVehicle(requestDTO, "aryan@test.com"));
    }

    @Test
    void shouldUpdateVehicleSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(vehicle));
        when(repo.save(vehicle)).thenReturn(vehicle);
        when(mapper.toDTO(vehicle)).thenReturn(responseDTO);

        VehicleResponseDTO result =
                service.updateVehicle(1L, requestDTO, "aryan@test.com");

        assertNotNull(result);
        assertEquals("MP04AB1234", vehicle.getLicensePlate());
        verify(repo).save(vehicle);
    }

    @Test
    void shouldThrowWhenUpdatingVehicleOfAnotherOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(vehicle));

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateVehicle(1L, requestDTO, "other@test.com"));
    }

    @Test
    void shouldDeleteVehicleSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(vehicle));

        service.deleteVehicle(1L, "aryan@test.com");

        verify(repo).delete(vehicle);
    }

    @Test
    void shouldDeactivateVehicleSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(vehicle));

        service.deactivateVehicle(1L, "aryan@test.com");

        assertFalse(vehicle.isActive());
        verify(repo).save(vehicle);
    }

    @Test
    void shouldGetVehicleByIdSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(vehicle));
        when(mapper.toDTO(vehicle)).thenReturn(responseDTO);

        VehicleResponseDTO result = service.getVehicleById(1L);

        assertNotNull(result);
    }

    @Test
    void shouldGetMyVehiclesSuccessfully() {
        when(repo.findByOwnerEmailAndIsActiveTrue("aryan@test.com"))
                .thenReturn(List.of(vehicle));
        when(mapper.toDTO(vehicle)).thenReturn(responseDTO);

        List<VehicleResponseDTO> result =
                service.getMyVehicles("aryan@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetByLicensePlateSuccessfully() {
        when(repo.findByLicensePlate("MP04AB1234"))
                .thenReturn(Optional.of(vehicle));
        when(mapper.toDTO(vehicle)).thenReturn(responseDTO);

        VehicleResponseDTO result =
                service.getByLicensePlate("mp04ab1234");

        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenVehicleNotFoundById() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getVehicleById(99L));
    }

    @Test
    void shouldThrowWhenVehicleNotFoundByPlate() {
        when(repo.findByLicensePlate("UNKNOWN")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getByLicensePlate("UNKNOWN"));
    }

    // JWT utility and filter coverage tests

    @Test
    void testJwtUtil() {
        com.parkease.vehicle.util.JwtUtil jwt = new com.parkease.vehicle.util.JwtUtil();
        String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "jwtSecret", secret);
        String token = io.jsonwebtoken.Jwts.builder()
            .setSubject("test@test.com")
            .claim("role", "ROLE_USER")
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes()))
            .compact();
            
        assertTrue(jwt.validate(token));
        assertEquals("test@test.com", jwt.getEmail(token));
        assertEquals("ROLE_USER", jwt.getRole(token));
        
        assertFalse(jwt.validate("invalidToken"));
    }

    @Test
    void testJwtAuthFilter() throws Exception {
        com.parkease.vehicle.util.JwtUtil jwt = mock(com.parkease.vehicle.util.JwtUtil.class);
        com.parkease.vehicle.security.JwtAuthFilter filter = new com.parkease.vehicle.security.JwtAuthFilter(jwt);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.validate("token")).thenReturn(true);
        when(jwt.getEmail("token")).thenReturn("t@t.com");
        when(jwt.getRole("token")).thenReturn("ROLE_ADMIN");

        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    @Test
    void testJwtAuthFilterInvalidToken() throws Exception {
        com.parkease.vehicle.util.JwtUtil jwt = mock(com.parkease.vehicle.util.JwtUtil.class);
        com.parkease.vehicle.security.JwtAuthFilter filter = new com.parkease.vehicle.security.JwtAuthFilter(jwt);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.validate("token")).thenThrow(new RuntimeException("invalid"));

        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    // Global exception handler and DTO coverage tests

    @Test
    void testGlobalExceptionHandler() {
        com.parkease.vehicle.exception.GlobalExceptionHandler handler = new com.parkease.vehicle.exception.GlobalExceptionHandler();
        assertNotNull(handler.handleGeneral(new Exception("test")));
        assertNotNull(handler.handleNotFound(new com.parkease.vehicle.exception.ResourceNotFoundException("test")));
        
        org.springframework.web.bind.MethodArgumentNotValidException ex = mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult result = mock(org.springframework.validation.BindingResult.class);
        when(ex.getBindingResult()).thenReturn(result);
        when(result.getFieldErrors()).thenReturn(List.of(new org.springframework.validation.FieldError("obj", "field", "msg")));
        assertNotNull(handler.handleValidation(ex));
    }

    @Test
    void testDTOs() {
        VehicleRequestDTO req = new VehicleRequestDTO();
        req.setMake("Hyundai");
        assertNotNull(req.getMake());
        assertNotNull(req.toString());

        VehicleResponseDTO res = new VehicleResponseDTO();
        res.setMake("Hyundai");
        assertNotNull(res.getMake());
        assertNotNull(res.toString());
    }

    @Test
    void testVehicleMapper() {
        com.parkease.vehicle.mapper.VehicleMapper realMapper = new com.parkease.vehicle.mapper.VehicleMapper();
        
        VehicleRequestDTO req = new VehicleRequestDTO();
        req.setLicensePlate(" mp04ab1234 ");
        req.setMake("Hyundai");
        req.setModel("i20");
        req.setColor("White");
        req.setVehicleType(VehicleType.FOUR_WHEELER);
        req.setEV(true);

        Vehicle v = realMapper.toEntity(req, "aryan@test.com");
        assertEquals("MP04AB1234", v.getLicensePlate());
        assertEquals("aryan@test.com", v.getOwnerEmail());
        assertEquals("Hyundai", v.getMake());
        assertTrue(v.isEV());
        assertTrue(v.isActive());

        VehicleResponseDTO res = realMapper.toDTO(v);
        assertEquals("MP04AB1234", res.getLicensePlate());
        assertEquals("aryan@test.com", res.getOwnerEmail());
        assertEquals("Hyundai", res.getMake());
        assertTrue(res.isEV());
        assertTrue(res.isActive());
    }

    @Test
    void testLombokMethods() {
    // Lombok-generated method coverage tests for entity and DTO equality, hashing, and toString
        Vehicle v1 = Vehicle.builder()
                .vehicleId(1L)
                .ownerEmail("test@test.com")
                .licensePlate("plate")
                .make("make")
                .model("model")
                .color("color")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .isEV(true)
                .isActive(true)
                .registeredAt(java.time.LocalDateTime.now())
                .build();
        Vehicle v2 = new Vehicle();
        v2.setVehicleId(1L);
        v2.setOwnerEmail("test@test.com");
        v2.setLicensePlate("plate");
        v2.setMake("make");
        v2.setModel("model");
        v2.setColor("color");
        v2.setVehicleType(VehicleType.FOUR_WHEELER);
        v2.setEV(true);
        v2.setActive(true);
        v2.setRegisteredAt(v1.getRegisteredAt());
        
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertNotNull(v1.toString());
        assertEquals("test@test.com", v2.getOwnerEmail());
        
        // VehicleRequestDTO coverage
        VehicleRequestDTO req1 = new VehicleRequestDTO();
        req1.setLicensePlate("plate");
        req1.setMake("make");
        req1.setModel("model");
        req1.setColor("color");
        req1.setVehicleType(VehicleType.FOUR_WHEELER);
        req1.setEV(true);
        
        VehicleRequestDTO req2 = new VehicleRequestDTO();
        req2.setLicensePlate("plate");
        req2.setMake("make");
        req2.setModel("model");
        req2.setColor("color");
        req2.setVehicleType(VehicleType.FOUR_WHEELER);
        req2.setEV(true);
        
        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertNotNull(req1.toString());
        assertEquals("make", req1.getMake());

        // VehicleResponseDTO coverage
        VehicleResponseDTO res1 = VehicleResponseDTO.builder()
                .vehicleId(1L)
                .ownerEmail("test")
                .licensePlate("plate")
                .make("make")
                .model("model")
                .color("color")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .isEV(true)
                .isActive(true)
                .registeredAt(java.time.LocalDateTime.now())
                .build();
        
        VehicleResponseDTO res2 = new VehicleResponseDTO();
        res2.setVehicleId(1L);
        res2.setOwnerEmail("test");
        res2.setLicensePlate("plate");
        res2.setMake("make");
        res2.setModel("model");
        res2.setColor("color");
        res2.setVehicleType(VehicleType.FOUR_WHEELER);
        res2.setEV(true);
        res2.setActive(true);
        res2.setRegisteredAt(res1.getRegisteredAt());
        
        assertEquals(res1, res2);
        assertEquals(res1.hashCode(), res2.hashCode());
        assertNotNull(res1.toString());

        // ApiResponse coverage
        com.parkease.vehicle.dto.response.ApiResponse api1 = com.parkease.vehicle.dto.response.ApiResponse.builder()
                .success(true)
                .message("msg")
                .build();
                
        com.parkease.vehicle.dto.response.ApiResponse api2 = new com.parkease.vehicle.dto.response.ApiResponse();
        api2.setSuccess(true);
        api2.setMessage("msg");
        
        assertEquals(api1, api2);
        assertEquals(api1.hashCode(), api2.hashCode());
        assertNotNull(api1.toString());
        assertTrue(api2.isSuccess());
        
        assertNotNull(com.parkease.vehicle.dto.response.ApiResponse.ok("ok"));
        assertNotNull(com.parkease.vehicle.dto.response.ApiResponse.fail("fail"));
    }
}