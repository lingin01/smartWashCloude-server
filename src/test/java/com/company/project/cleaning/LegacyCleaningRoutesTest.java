package com.company.project.cleaning;

import com.company.project.controller.CleaningController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 旧模拟查询必须撤销路由，而不是继续返回模拟值或“成功但为空”。 */
class LegacyCleaningRoutesTest {
    @Test void oldPageQueriesAreNotExposed() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CleaningController()).build();
        for (String path : List.of("dashboard", "machines/brieflyInfo", "machine/detailsInfo",
                "tanks/brieflyInfo", "tank/detailsInfo", "plans/brieflyInfo", "plan/detailsInfo",
                "tasks/brieflyInfo", "task/detailsInfo", "records/brieflyInfo", "record/detailsInfo")) {
            mvc.perform(post("/api/cleaning/" + path).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test void legacyControlGuardsStillGiveExplicitInstructions() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CleaningController()).build();
        mvc.perform(post("/api/cleaning/start").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/cleaning/stop").contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/cleaning/reset").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
