package com.sandeeprathore.reviewagent.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sandeeprathore.reviewagent.llm.NoModelAvailableException;
import com.sandeeprathore.reviewagent.review.diff.InvalidPatchException;

/** Maps domain failures to RFC 9457 problem responses. */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(InvalidPatchException.class)
	ProblemDetail invalidPatch(InvalidPatchException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid patch", ex);
	}

	@ExceptionHandler(PatchTooLargeException.class)
	ProblemDetail patchTooLarge(PatchTooLargeException ex) {
		return problem(HttpStatus.CONTENT_TOO_LARGE, "Patch too large", ex);
	}

	@ExceptionHandler(NoModelAvailableException.class)
	ProblemDetail noModel(NoModelAvailableException ex) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "No model available", ex);
	}

	private static ProblemDetail problem(HttpStatus status, String title, Exception ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setTitle(title);
		return problem;
	}

}
