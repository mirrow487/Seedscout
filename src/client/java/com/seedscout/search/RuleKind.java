package com.seedscout.search;

/** How a rule is used when judging a seed. */
public enum RuleKind {
	/** Must be within the distance, otherwise the seed is rejected. Also adds score (closer = more). */
	REQUIRED,
	/** Optional: adds score when within the distance (closer = more). */
	PREFERRED,
	/** Must NOT be within the distance (e.g. "no ocean within 64 blocks of spawn"). */
	FORBIDDEN
}
