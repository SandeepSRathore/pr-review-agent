package com.sandeeprathore.reviewagent.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sandeeprathore.reviewagent.orchestration.ReviewProperties;
import com.sandeeprathore.reviewagent.orchestration.ReviewReport;
import com.sandeeprathore.reviewagent.orchestration.ReviewService;
import com.sandeeprathore.reviewagent.review.diff.UnifiedDiffParser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = LocalReviewController.class, properties = "review.reviewer.max-patch-bytes=400")
@Import(UnifiedDiffParser.class)
@EnableConfigurationProperties(ReviewProperties.class)
class LocalReviewControllerTest {

	private static final String PATCH = """
			diff --git a/A.java b/A.java
			--- a/A.java
			+++ b/A.java
			@@ -1 +1 @@
			-class A {}
			+class A { }
			""";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReviewService reviewService;

	@Test
	void reviewsARawPatch() throws Exception {
		given(reviewService.review(any())).willReturn(new ReviewReport("id-1", "Looks fine.", List.of(), List.of(),
				new ReviewReport.Stats(1, 0, 1, 100, 20, 5, "anthropic/m", "abcd1234")));

		mvc.perform(post("/api/reviews/local").contentType("text/x-diff").content(PATCH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewId").value("id-1"))
			.andExpect(jsonPath("$.stats.promptVersion").value("abcd1234"));
	}

	@Test
	void rejectsSomethingThatIsNotADiffAsABadRequest() throws Exception {
		mvc.perform(post("/api/reviews/local").contentType(MediaType.TEXT_PLAIN).content("please review my code"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Invalid patch"));
	}

	@Test
	void rejectsPatchesOverTheSizeLimit() throws Exception {
		mvc.perform(post("/api/reviews/local").contentType("text/x-diff").content(PATCH + "x".repeat(500)))
			.andExpect(status().is(413));
	}

	@Test
	void validatesTheJsonBody() throws Exception {
		mvc.perform(post("/api/reviews/local").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\"}"))
			.andExpect(status().isBadRequest());
	}

}
