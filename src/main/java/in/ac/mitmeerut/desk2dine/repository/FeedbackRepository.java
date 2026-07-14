package in.ac.mitmeerut.desk2dine.repository;

import in.ac.mitmeerut.desk2dine.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}