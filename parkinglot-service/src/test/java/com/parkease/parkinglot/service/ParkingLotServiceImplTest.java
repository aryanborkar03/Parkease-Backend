package com.parkease.parkinglot.service;

import com.parkease.parkinglot.dto.request.ParkingLotRequestDTO;
import com.parkease.parkinglot.dto.response.ParkingLotResponseDTO;
import com.parkease.parkinglot.entity.ParkingLot;
import com.parkease.parkinglot.exception.ResourceNotFoundException;
import com.parkease.parkinglot.exception.UnauthorizedException;
import com.parkease.parkinglot.mapper.ParkingLotMapper;
import com.parkease.parkinglot.repository.ParkingLotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotServiceImplTest {

    @Mock
    private ParkingLotRepository repo;

    @Mock
    private ParkingLotMapper mapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ParkingLotServiceImpl service;

    private ParkingLot lot;
    private ParkingLotResponseDTO responseDTO;
    private ParkingLotRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        lot = ParkingLot.builder()
                .lotId(1L)
                .name("City Mall Parking")
                .managerEmail("manager@test.com")
                .city("Bhopal")
                .latitude(23.2599)
                .longitude(77.4126)
                .totalSpots(100)
                .availableSpots(50)
                .isApproved(true)
                .isOpen(true)
                .build();

        responseDTO = new ParkingLotResponseDTO();

        requestDTO = new ParkingLotRequestDTO();
        requestDTO.setName("City Mall Parking");
        requestDTO.setAddress("MP Nagar");
        requestDTO.setCity("Bhopal");
        requestDTO.setLatitude(23.2599);
        requestDTO.setLongitude(77.4126);
        requestDTO.setTotalSpots(100);
        requestDTO.setOpenTime(LocalTime.of(8, 0));
        requestDTO.setCloseTime(LocalTime.of(22, 0));
        requestDTO.setImageUrl("image.jpg");
    }

    @Test
    void shouldCreateLotSuccessfully() {
        when(mapper.toEntity(requestDTO, "manager@test.com")).thenReturn(lot);
        when(repo.save(lot)).thenReturn(lot);
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        ParkingLotResponseDTO result =
                service.createLot(requestDTO, "manager@test.com");

        assertNotNull(result);
        verify(repo).save(lot);
    }

    @Test
    void shouldGetLotByIdSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        ParkingLotResponseDTO result = service.getLotById(1L);

        assertNotNull(result);
    }

    @Test
    void shouldGetLotsByCitySuccessfully() {
        when(repo.findByCityIgnoreCaseAndIsApprovedTrue("Bhopal"))
                .thenReturn(List.of(lot));
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        List<ParkingLotResponseDTO> result = service.getByCity("Bhopal", null, null, null);

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetNearbyLotsSuccessfully() {
        when(repo.findNearby(23.2599, 77.4126, 5.0))
                .thenReturn(List.of(lot));
        when(mapper.toDTO(eq(lot), anyDouble()))
                .thenReturn(responseDTO);

        List<ParkingLotResponseDTO> result =
                service.getNearbyLots(23.2599, 77.4126, 5.0, null, null, null);

        assertEquals(1, result.size());
    }

    @Test
    void shouldUpdateLotSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        when(repo.save(lot)).thenReturn(lot);
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        ParkingLotResponseDTO result =
                service.updateLot(1L, requestDTO, "manager@test.com");

        assertNotNull(result);
        verify(repo).save(lot);
    }

    @Test
    void shouldThrowWhenUpdatingAnotherManagersLot() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));

        assertThrows(UnauthorizedException.class,
                () -> service.updateLot(1L, requestDTO, "other@test.com"));
    }

    @Test
    void shouldDeleteLotSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));

        service.deleteLot(1L, "manager@test.com");

        verify(repo).delete(lot);
    }

    @Test
    void shouldToggleLotOpenSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        when(repo.save(lot)).thenReturn(lot);
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        ParkingLotResponseDTO result =
                service.toggleOpen(1L, "manager@test.com");

        assertNotNull(result);
        assertFalse(lot.isOpen());
    }

    @Test
    void shouldApproveLotSuccessfully() {
        lot.setApproved(false);

        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        when(repo.save(lot)).thenReturn(lot);
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        ParkingLotResponseDTO result = service.approveLot(1L);

        assertNotNull(result);
        assertTrue(lot.isApproved());
    }

    @Test
    void shouldRejectLotSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        when(repo.save(lot)).thenReturn(lot);
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        ParkingLotResponseDTO result = service.rejectLot(1L);

        assertNotNull(result);
        assertFalse(lot.isApproved());
        assertFalse(lot.isOpen());
    }

    @Test
    void shouldDecrementSpotSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));

        service.decrementSpot(1L);

        assertEquals(49, lot.getAvailableSpots());
        verify(repo).save(lot);
    }

    @Test
    void shouldIncrementSpotSuccessfully() {
        when(repo.findById(1L)).thenReturn(Optional.of(lot));

        service.incrementSpot(1L);

        assertEquals(51, lot.getAvailableSpots());
        verify(repo).save(lot);
    }

    @Test
    void shouldDecrementSpotThrowWhenEmpty() {
        lot.setAvailableSpots(0);
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        assertThrows(IllegalStateException.class, () -> service.decrementSpot(1L));
    }

    @Test
    void shouldIncrementSpotSkipWhenFull() {
        lot.setAvailableSpots(100);
        lot.setTotalSpots(100);
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        service.incrementSpot(1L);
        assertEquals(100, lot.getAvailableSpots());
        verify(repo, never()).save(lot);
    }

    @Test
    void shouldToggleLotOpenThrowWhenNotApproved() {
        lot.setApproved(false);
        when(repo.findById(1L)).thenReturn(Optional.of(lot));
        assertThrows(UnauthorizedException.class, () -> service.toggleOpen(1L, "manager@test.com"));
    }



    // JWT utility and filter coverage tests

    @Test
    void testJwtUtil() {
        com.parkease.parkinglot.util.JwtUtil jwt = new com.parkease.parkinglot.util.JwtUtil();
        String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "jwtSecret", secret);
        String token = io.jsonwebtoken.Jwts.builder()
            .setSubject("test@test.com")
            .claim("role", "ROLE_USER")
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes()))
            .compact();
            
        assertTrue(jwt.validateToken(token));
        assertEquals("test@test.com", jwt.getEmailFromToken(token));
        assertEquals("ROLE_USER", jwt.getRoleFromToken(token));
        
        assertFalse(jwt.validateToken("invalidToken"));
    }

    @Test
    void testJwtAuthFilter() throws Exception {
        com.parkease.parkinglot.util.JwtUtil jwt = mock(com.parkease.parkinglot.util.JwtUtil.class);
        com.parkease.parkinglot.security.JwtAuthFilter filter = new com.parkease.parkinglot.security.JwtAuthFilter(jwt);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.validateToken("token")).thenReturn(true);
        when(jwt.getEmailFromToken("token")).thenReturn("t@t.com");
        when(jwt.getRoleFromToken("token")).thenReturn("ROLE_ADMIN");

        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    @Test
    void testJwtAuthFilterInvalidToken() throws Exception {
        com.parkease.parkinglot.util.JwtUtil jwt = mock(com.parkease.parkinglot.util.JwtUtil.class);
        com.parkease.parkinglot.security.JwtAuthFilter filter = new com.parkease.parkinglot.security.JwtAuthFilter(jwt);

        jakarta.servlet.http.HttpServletRequest req = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse res = mock(jakarta.servlet.http.HttpServletResponse.class);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.validateToken("token")).thenThrow(new RuntimeException("invalid"));

        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }

    // Global exception handler and DTO coverage tests

    @Test
    void testGlobalExceptionHandler() {
        com.parkease.parkinglot.exception.GlobalExceptionHandler handler = new com.parkease.parkinglot.exception.GlobalExceptionHandler();
        assertNotNull(handler.handleGeneral(new Exception("test")));
        assertNotNull(handler.handleNotFound(new com.parkease.parkinglot.exception.ResourceNotFoundException("test")));
        assertNotNull(handler.handleUnauthorized(new com.parkease.parkinglot.exception.UnauthorizedException("test")));
        // Mocks MethodArgumentNotValidException to test validation error handling
        org.springframework.web.bind.MethodArgumentNotValidException ex = mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult result = mock(org.springframework.validation.BindingResult.class);
        when(ex.getBindingResult()).thenReturn(result);
        when(result.getFieldErrors()).thenReturn(List.of(new org.springframework.validation.FieldError("obj", "field", "msg")));
        assertNotNull(handler.handleValidation(ex));
    }

    @Test
    void testDTOs() {
        ParkingLotRequestDTO req = new ParkingLotRequestDTO();
        req.setName("Test");
        assertNotNull(req.getName());

        ParkingLotResponseDTO res = new ParkingLotResponseDTO();
        res.setName("Test");
        assertNotNull(res.getName());
    }

    // Missing branch coverage tests

    @Test
    void shouldThrowWhenLotNotFoundById() {
        when(repo.findById(99L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getLotById(99L));
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentLot() {
        when(repo.findById(99L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateLot(99L, requestDTO, "manager@test.com"));
    }

    @Test
    void shouldThrowWhenDeletingNonExistentLot() {
        when(repo.findById(99L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteLot(99L, "manager@test.com"));
    }

    @Test
    void shouldThrowWhenDeletingAnotherManagersLot() {
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(lot));
        assertThrows(UnauthorizedException.class,
                () -> service.deleteLot(1L, "other@test.com"));
    }

    @Test
    void shouldThrowWhenTogglingAnotherManagersLot() {
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(lot));
        assertThrows(UnauthorizedException.class,
                () -> service.toggleOpen(1L, "other@test.com"));
    }

    @Test
    void shouldGetOpenLots() {
        when(repo.findByIsOpenTrueAndIsApprovedTrue()).thenReturn(List.of(lot));
        when(mapper.toDTO(lot)).thenReturn(responseDTO);
        // RestTemplate returns null — triggers the zero-spot fallback branch
        when(restTemplate.getForObject(anyString(), eq(Long.class), eq(1L))).thenReturn(null);

        List<ParkingLotResponseDTO> result = service.getOpenLots();
        assertEquals(1, result.size());
    }

    @Test
    void shouldGetLotsByManager() {
        when(repo.findByManagerEmail("manager@test.com")).thenReturn(List.of(lot));
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        List<ParkingLotResponseDTO> result = service.getLotsByManager("manager@test.com");
        assertEquals(1, result.size());
    }

    @Test
    void shouldGetPendingApprovalLots() {
        lot.setApproved(false);
        when(repo.findByIsApprovedFalse()).thenReturn(List.of(lot));
        when(mapper.toDTO(lot)).thenReturn(responseDTO);

        List<ParkingLotResponseDTO> result = service.getPendingApprovalLots();
        assertEquals(1, result.size());
    }

}