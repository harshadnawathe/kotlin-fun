package com.github.harshadnawathe.delegate

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.KProperty

interface InitOnce<T> {
    var value: T
    val isInitialized: Boolean
}

@Suppress("ClassName")
internal object UNINITIALIZED_VALUE

class SynchronizedInitOnce<T> : InitOnce<T> {

    @Volatile
    private var _value: Any? = UNINITIALIZED_VALUE

    override val isInitialized: Boolean
        get() = _value !== UNINITIALIZED_VALUE

    override var value: T
        get() {
            val v = _value
            if (v !== UNINITIALIZED_VALUE) {
                @Suppress("UNCHECKED_CAST")
                return v as T
            }
            throw IllegalStateException("Property is not initialized")
        }
        set(value) {
            val _v1 = _value
            if (_v1 !== UNINITIALIZED_VALUE) {
                throw IllegalStateException("Property is already initialized")
            }
            synchronized(this) {
                val _v2 = _value
                if (_v2 !== UNINITIALIZED_VALUE) {
                    throw IllegalStateException("Property is already initialized")
                }
                _value = value
            }
        }
}

inline operator fun <reified T> InitOnce<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value
inline operator fun <reified T> InitOnce<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}
inline fun <reified T> initOnce(): InitOnce<T> = SynchronizedInitOnce()


//-----------------------------------------------

class Task {
    var assignee: String by initOnce()
}


class InitOnceTest {

    @Test
    fun `on read should throw exception when property is not initialized`() {
        val t = Task()

        assertThatThrownBy {
            t.assignee
        }.hasMessage("Property is not initialized")
    }

    @Test
    fun `on read should not throw exception when property is initialized`() {
        val t = Task().apply {
            assignee = "Clark Kent"
        }

        assertThatCode {
            t.assignee
        }.doesNotThrowAnyException()
    }

    @Test
    fun `on read should return value if property is initialized`() {
        val t = Task().apply {
            assignee = "Clark Kent"
        }

        assertThat(t.assignee).isEqualTo("Clark Kent")
    }

    @Test
    fun `on write should assign value if property is not initialized`() {
        val t = Task()

        t.assignee = "Bruce Wayne"

        assertThat(t.assignee).isEqualTo("Bruce Wayne")
    }

    @Test
    fun `on write should throw exception when property is initialized already`() {
        val t = Task().apply {
            assignee = "Bruce Wayne"
        }

        assertThatThrownBy {
            t.assignee = "Clark Kent"
        }.hasMessage("Property is already initialized")
    }
}