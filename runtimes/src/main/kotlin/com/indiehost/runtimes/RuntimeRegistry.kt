package com.indiehost.runtimes

import com.indiehost.detect.Engine

/**
 * The one place that decides which adapter owns an engine.
 *
 * There is no reflection and no default adapter: an engine that is detected but
 * not registered stays visibly unavailable instead of silently doing nothing.
 */
class RuntimeRegistry(private val adapters: List<RuntimeAdapter>) {

    fun forEngine(engine: Engine): RuntimeAdapter? = adapters.firstOrNull { it.engine == engine }
}
