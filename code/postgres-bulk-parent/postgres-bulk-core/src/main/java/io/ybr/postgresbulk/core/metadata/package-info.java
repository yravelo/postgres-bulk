/**
 * Neutral metadata SPI for describing bulk rows independently of persistence frameworks and
 * database mechanisms.
 *
 * <p>Adapters produce immutable descriptors in this package after resolving their native mapping.
 * Consumers can then read ordered values without performing reflection or understanding the
 * originating framework.
 */
package io.ybr.postgresbulk.core.metadata;
