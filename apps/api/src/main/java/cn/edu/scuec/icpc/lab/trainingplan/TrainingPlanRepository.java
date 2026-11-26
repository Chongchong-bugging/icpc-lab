package cn.edu.scuec.icpc.lab.trainingplan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, UUID> {

    List<TrainingPlan> findAllByOrderByCreatedAtDesc();
}