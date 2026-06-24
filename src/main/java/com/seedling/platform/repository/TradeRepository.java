package com.seedling.platform.repository;

import com.seedling.platform.model.Trade;
import com.seedling.platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByUserOrderByTimestampDesc(User user);
    List<Trade> findByUserAndTimestampAfter(User user, LocalDateTime timestamp);
}
