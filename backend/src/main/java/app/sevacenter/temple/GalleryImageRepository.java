package app.sevacenter.temple;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS: only the current tenant's photos. Listing never loads the bytes. */
public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {

    interface Summary {
        Long getId();
        String getSection();
        String getCaption();
        int getWidth();
        int getHeight();
        String getVersion();
    }

    @Query("select g.id as id, g.section as section, g.caption as caption, g.width as width, g.height as height, "
            + "g.version as version from GalleryImage g order by g.section, g.id")
    List<Summary> summaries();

    long countBySection(String section);
}
