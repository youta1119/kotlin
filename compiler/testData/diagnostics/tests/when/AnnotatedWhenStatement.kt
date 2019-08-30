/*
 * RELEVANT SPEC SENTENCES (spec version: 0.1-152, test type: positive):
 *  - expressions, when-expression -> paragraph 9 -> sentence 2
 *  - expressions, when-expression -> paragraph 5 -> sentence 1
 *  - expressions, when-expression -> paragraph 6 -> sentence 5
 *  - annotations, annotation-targets -> paragraph 1 -> sentence 1
 */
fun foo(a: Int) {
    @ann
    when (a) {
        1 -> {}
    }
}

annotation class ann
