package com.kabadiwala.backend.transaction;

import com.kabadiwala.backend.auth.UserPrincipal;
import com.kabadiwala.backend.auth.UserRole;
import com.kabadiwala.backend.common.InvalidStateTransitionException;
import com.kabadiwala.backend.material.LotStatus;
import com.kabadiwala.backend.material.MaterialLot;
import com.kabadiwala.backend.material.MaterialLotRepository;
import com.kabadiwala.backend.recycler.Recycler;
import com.kabadiwala.backend.recycler.RecyclerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverServiceTest {

    @Mock
    private HandoverTransactionRepository transactionRepository;

    @Mock
    private MaterialLotRepository materialLotRepository;

    @Mock
    private RecyclerRepository recyclerRepository;

    private HandoverService handoverService;
    private UUID collectorId;
    private UUID recyclerId;

    @BeforeEach
    void setUp() {
        handoverService = new HandoverService(transactionRepository, materialLotRepository, recyclerRepository);
        collectorId = UUID.randomUUID();
        recyclerId = UUID.randomUUID();

        UserPrincipal principal = new UserPrincipal(collectorId, "collector@kabadiwala.org", UserRole.COLLECTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Initiate handover: creates transaction and transitions lot to HANDED_OVER")
    void testInitiateHandover_Success() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.READY_FOR_HANDOVER);
        lot.setWeightKg(new BigDecimal("5.000"));
        lot.setPricePerKg(new BigDecimal("180.00"));
        lot.setEstimatedPrice(new BigDecimal("900.00"));

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        Recycler recycler = new Recycler("Green E-Waste Hub", "Mumbai", "9876543210", List.of("PCB"));
        recycler.setId(recyclerId);
        when(recyclerRepository.findById(recyclerId)).thenReturn(Optional.of(recycler));

        when(transactionRepository.save(any())).thenAnswer(i -> {
            HandoverTransaction tx = i.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        InitiateHandoverRequest request = new InitiateHandoverRequest(lotId, recyclerId, "Initial handover test");
        HandoverTransactionDto result = handoverService.initiateHandover(collectorId, request);

        assertNotNull(result);
        assertEquals(lotId, result.lotId());
        assertEquals(recyclerId, result.recyclerId());
        assertEquals(HandoverStatus.INITIATED, result.status());
        assertEquals(LotStatus.HANDED_OVER, lot.getStatus());
    }

    @Test
    @DisplayName("Initiate handover: throws exception if lot is not READY_FOR_HANDOVER")
    void testInitiateHandover_ThrowsIfNotReady() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.PRICED); // Not yet marked ready

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        InitiateHandoverRequest request = new InitiateHandoverRequest(lotId, recyclerId, "premature");
        assertThrows(InvalidStateTransitionException.class, () ->
                handoverService.initiateHandover(collectorId, request));
    }

    @Test
    @DisplayName("Accept handover: completes transaction and settles lot final price")
    void testAcceptHandover_Success() {
        UUID txId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.HANDED_OVER);

        HandoverTransaction tx = new HandoverTransaction(
                lotId, collectorId, recyclerId,
                new BigDecimal("5.000"), new BigDecimal("180.00"), new BigDecimal("900.00")
        );
        tx.setId(txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Recycler weighs slightly higher: 5.2 kg at agreed rate 180 = 936.00
        AcceptHandoverRequest acceptRequest = new AcceptHandoverRequest(
                new BigDecimal("5.200"),
                new BigDecimal("180.00"),
                "Weighed on digital scale"
        );

        HandoverTransactionDto completed = handoverService.acceptHandover(txId, acceptRequest);

        assertEquals(HandoverStatus.COMPLETED, completed.status());
        assertEquals(new BigDecimal("936.00"), completed.totalAmount());
        assertEquals(LotStatus.COMPLETED, lot.getStatus());
        assertEquals(new BigDecimal("936.00"), lot.getFinalPrice());
    }

    @Test
    @DisplayName("Reject handover: sets transaction to REJECTED and reverts lot to READY_FOR_HANDOVER")
    void testRejectHandover_Success() {
        UUID txId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.HANDED_OVER);

        HandoverTransaction tx = new HandoverTransaction(
                lotId, collectorId, recyclerId,
                new BigDecimal("5.000"), new BigDecimal("180.00"), new BigDecimal("900.00")
        );
        tx.setId(txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        HandoverTransactionDto rejected = handoverService.rejectHandover(txId, "Contaminated batch");

        assertEquals(HandoverStatus.REJECTED, rejected.status());
        assertEquals(LotStatus.READY_FOR_HANDOVER, lot.getStatus());
    }
}
