package pt.isep.sidis.aircraft.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceTemplateRepository extends JpaRepository<MaintenanceTemplate, Long> {

    Optional<MaintenanceTemplate> findByTemplateName(String templateName);

    boolean existsByTemplateName(String templateName);

    List<MaintenanceTemplate> findByTemplateType(TemplateType templateType);

    @Query("SELECT t FROM MaintenanceTemplate t JOIN t.applicableModels m WHERE m.id = :modelId")
    List<MaintenanceTemplate> findByApplicableModel(@Param("modelId") Long modelId);
}
