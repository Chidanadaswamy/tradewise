package com.seedling.platform.repository;

import com.seedling.platform.model.User;
import com.seedling.platform.model.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUserOrderByAddedAtDesc(User user);
    Optional<Watchlist> findByUserAndTicker(User user, String ticker);
    void deleteByUserAndTicker(User user, String ticker);
    long countByUser(User user);
    boolean existsByUserAndTicker(User user, String ticker);
}
