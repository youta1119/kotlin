interface A {
    fun foo()
}

interface B : A {
    fun bar()
}

interface C : A {
    fun baz()
}

fun test_1(x: Any) {
    if (x is B && x is C) {
        x.foo()
        x.bar()
        x.baz()
    }
}

fun test_2(x: Any) {
    if (x is B || x is C) {
        x.foo()
        x.bar()
        x.baz()
    }
}