package com.sandeeprathore.reviewagent.review.finding;

/** Ordered most to least severe, so {@code compareTo} sorts critical findings first. */
public enum Severity {
	CRITICAL, MAJOR, MINOR, NIT
}
