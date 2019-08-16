fun test_1(x: Any?) {
    x as String
    x.length
}

fun test_2(x: Any?) {
    if (x as Boolean) {
        x.not()
    }
    x.not()
}

fun test_3(b: Boolean, x: Any?) {
    if (b && x as Boolean) {
        x.not()
    }
    x.not()

    if (b && x as Boolean == true) {
        x.not()
    }
    x.not()

    if (b || x as Boolean) {
        x.not()
    }
    x.not()
}