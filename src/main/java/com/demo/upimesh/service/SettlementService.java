package com.demo.upimesh.service;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;

/**
 * Handles the actual ledger update.
 *
 * Each browser session has its own isolated demo accounts and transactions.
 *
 * The @Version field on Account provides optimistic locking so concurrent
 * updates cannot silently overwrite each other's balances.
 */
@Service
public class SettlementService {

    private static final Logger log =
            LoggerFactory.getLogger(SettlementService.class);

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TransactionRepository transactions;

    /**
     * Settles a payment inside one database transaction.
     *
     * The sessionId identifies the browser/demo session whose accounts
     * should be used.
     */
    @Transactional
    public Transaction settle(
            PaymentInstruction instruction,
            String packetHash,
            String bridgeNodeId,
            int hopCount,
            String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }

        Account sender = accounts
                .findBySessionIdAndVpa(
                        sessionId,
                        instruction.getSenderVpa()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown sender VPA: "
                                + instruction.getSenderVpa()
                ));

        Account receiver = accounts
                .findBySessionIdAndVpa(
                        sessionId,
                        instruction.getReceiverVpa()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown receiver VPA: "
                                + instruction.getReceiverVpa()
                ));

        BigDecimal amount = instruction.getAmount();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        if (sender.getBalance().compareTo(amount) < 0) {

            log.warn(
                    "Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(),
                    sender.getBalance(),
                    amount
            );

            return recordRejected(
                    instruction,
                    packetHash,
                    bridgeNodeId,
                    hopCount,
                    sessionId
            );
        }

        /*
         * Debit sender and credit receiver.
         */
        sender.setBalance(
                sender.getBalance().subtract(amount)
        );

        receiver.setBalance(
                receiver.getBalance().add(amount)
        );

        accounts.save(sender);
        accounts.save(receiver);

        /*
         * Record successful transaction.
         */
        Transaction tx = new Transaction();

        tx.setSessionId(sessionId);
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(amount);

        tx.setSignedAt(
                Instant.ofEpochMilli(
                        instruction.getSignedAt()
                )
        );

        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.SETTLED);

        transactions.save(tx);

        log.info(
                "SETTLED ₹{} from {} to {} " +
                "(session={}, packetHash={}, bridge={}, hops={})",
                amount,
                sender.getVpa(),
                receiver.getVpa(),
                sessionId,
                packetHash.substring(0, 12) + "...",
                bridgeNodeId,
                hopCount
        );

        return tx;
    }

    /**
     * Records a rejected transaction.
     */
    private Transaction recordRejected(
            PaymentInstruction instruction,
            String packetHash,
            String bridgeNodeId,
            int hopCount,
            String sessionId) {

        Transaction tx = new Transaction();

        tx.setSessionId(sessionId);
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());

        tx.setSignedAt(
                Instant.ofEpochMilli(
                        instruction.getSignedAt()
                )
        );

        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.REJECTED);

        return transactions.save(tx);
    }

    /**
     * Resets only the demo data belonging to the current browser session.
     *
     * IMPORTANT:
     * We intentionally do NOT call transactions.deleteAll(),
     * because that would delete other users' transactions on Railway.
     */
    @Transactional
    public void resetDemoAccounts(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Session ID is required"
            );
        }

        int deletedTransactions = transactions.deleteBySessionId(sessionId);

System.out.println(
    "🧹 Deleted " + deletedTransactions
    + " transactions for session " + sessionId
);

        Account alice = accounts
                .findBySessionIdAndVpa(
                        sessionId,
                        "alice@demo"
                )
                .orElseThrow();

        Account bob = accounts
                .findBySessionIdAndVpa(
                        sessionId,
                        "bob@demo"
                )
                .orElseThrow();

        Account carol = accounts
                .findBySessionIdAndVpa(
                        sessionId,
                        "carol@demo"
                )
                .orElseThrow();

        Account dave = accounts
                .findBySessionIdAndVpa(
                        sessionId,
                        "dave@demo"
                )
                .orElseThrow();

        alice.setBalance(new BigDecimal("5000.00"));
        bob.setBalance(new BigDecimal("1500.00"));
        carol.setBalance(new BigDecimal("2500.00"));
        dave.setBalance(new BigDecimal("500.00"));

        accounts.save(alice);
        accounts.save(bob);
        accounts.save(carol);
        accounts.save(dave);

        log.info(
                "Demo accounts reset for session {}",
                sessionId
        );
    }
}