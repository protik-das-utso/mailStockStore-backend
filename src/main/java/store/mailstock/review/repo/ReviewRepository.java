package store.mailstock.review.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import store.mailstock.review.entity.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByInventoryIdAndApprovedTrueOrderByIdDesc(Long id);
    List<Review> findByApprovedFalseOrderByIdDesc();
    List<Review> findTop6ByApprovedTrueOrderByIdDesc();
    long countByApprovedFalse();
}
