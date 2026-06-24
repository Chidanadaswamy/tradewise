package com.seedling.platform.repository;

import com.seedling.platform.model.Position;
import com.seedling.platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUser(User user);
    Optional<Position> findByUserAndTicker(User user, String ticker);
    List<Position> findByStopLossPriceIsNotNull();
}
