package com.parkease.payment.service;

import com.parkease.payment.dto.request.PayWithPassRequest;
import com.parkease.payment.dto.request.VerifyPassRequest;
import com.parkease.payment.dto.response.OrderResponseDTO;
import com.parkease.payment.dto.response.PassBalanceDTO;
import com.parkease.payment.dto.response.PassTransactionDTO;

import java.util.List;

public interface PassService {
    OrderResponseDTO createPassOrder(String driverEmail);
    PassBalanceDTO activatePass(VerifyPassRequest request, String driverEmail);
    PassBalanceDTO getMyPass(String driverEmail);
    PassTransactionDTO payWithPass(PayWithPassRequest request, String driverEmail);
    List<PassTransactionDTO> getMyPassTransactions(String driverEmail);
    List<PassTransactionDTO> getMyCurrentPassTransactions(String driverEmail);
    PassTransactionDTO getPassTransactionByBooking(Long bookingId, String driverEmail);
    String getPassReceiptPath(Long txnId, String driverEmail);
    void cancelPass(String driverEmail);
}
