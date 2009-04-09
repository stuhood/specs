package org.specs.specification
import org.specs.matcher._
import org.specs.matcher.Matchers._
import org.specs.ExtendedThrowable._
import org.specs.Sugar._
import org.specs.execute._

/**
 * An expectable is an object supporting the execution of expectations through matchers.<pre>
 *   thisExpectable must passMatcher
 * </pre>
 * It can be optionally related to an example when created for an anonymous example.
 * Otherwise it just fires a FailureException when failing:<pre>
 * object spec extends Specification {
 *   // is automatically related to an anonymous example
 *   // it will be executed only once the example is executed
 *   // @see org.specs.specification.ExpectableFactory
 *   // @see org.specs.specification.ExampleExpectationsListener
 *   1 must_== 1
 *
 *   // in that case, no example is set but during the execution of the "in" part
 *   // the failure exception will be caught by the example and stored
 *   "this example fails" in { 1 must_== 0 }
 * }
 * object test extends SpecsMatchers {
 *   // this expectable is not related to any example and executes right away throwing an exception if failing
 *   1 must_== 1
 * }
 * </pre>
 */
class Expectable[T](value: => T) {
  /** related example. */
  private var example: Option[Example] = None
  /** function used to display success values as a result of a match. By default nothing is displayed. */
  private var successValueToString: SuccessValue => String = s => ""
  /** the listener will be called for every match to register a new Expectation. */
  private var expectationsListener: Option[ExampleExpectationsListener] = None
  /**
   * stores a precise description of the thing being expected.
   * This description is meant to be passed to the matcher for better failure reporting.
   */
  protected var description: Option[String] = None
  private var not = false
  /**
   * Apply a matcher for this expectable value.
   *
   * Execute the matcher directly or add it to its related example for execution.
   * It either throws a FailureException or return a SuccessValue object.
   *
   * The expectation listener gets notified of a new expectation with a fresh copy of this expectable.
   * The matcher gets
   */
  def applyMatcher(m: => Matcher[T]): Result[T] = {
    val failureTemplate = FailureException("")
    val matcher = m
    matcher match {
      case okMatcher: org.specs.matcher.OkWordMatcher[_] => return new Result(this, successValueToString)
      case notMatcher: org.specs.matcher.NotMatcher[_] => { not = true; return new Result(this, successValueToString) }
      case _ => expectationsListener.map(_.addExpectation(Some(this)))
    }

    def executeMatch = {
      matcher.setDescription(description)
      val (result, _, koMessage) = if (not) matcher.not.apply(value) else matcher.apply(value)
      result match {
        case false => {
          new FailureException(koMessage).throwWithStackTraceOf(failureTemplate.removeTracesAsFarAsNameMatches("Expectable"))
        }
        case _ => new Result(this, successValueToString)
      }
    }
    example match {
      case None => executeMatch
      case Some(e) => {
        var res = new Result(this, successValueToString)
        e in { res = executeMatch }
        res
      }
    }
  }
  /**
   * Set a specific example to hold the results of this matcher
   */
  def setExample[T](ex: Example) = example = Some(ex)

  /** setter for the expectation listener. */
  def setExpectationsListener(listener: ExampleExpectationsListener): this.type = {
    expectationsListener = Some(listener)
    this
  }

  /**
   * Set a new function to render success values
   */
  def setSuccessValueToString(f: SuccessValue =>String) = successValueToString = f
}
/**
 * The Expect class adds matcher methods to objects which are being specified<br>
 * Usage: <code>new Expect(value, example) must beMatching(otherValue)</code><p>
 *
 * An assert is created with its parent <code>Example</code> in order to register failures
 * and errors if a matcher is not ok
 *
 */
class Expectation[T](value: => T) extends Expectable[T](value) {

  override def toString() = value.toString
  def createClone = new Expectation(value)

  /** set a better description on the value. */
  def aka(desc: String): this.type = { description = Some(desc); this }

  /**
   * applies a matcher to the current value and throw a failure is the result is not true
   */
  def must(m: => Matcher[T]) = applyMatcher(m)
  /**
   * applies the negation of a matcher
   */
  def mustNot(m: => Matcher[T]) =  must(m.not)

  /** alias for <code>must verify(f)</code>  */
  def mustVerify(f: T => Boolean) = must(verify(f))

  /** alias for <code>mustVerify(f)</code>  */
  def verifies(f: T => Boolean) = mustVerify(f)

  /** alias for <code>must be(other)</code>  */
  def mustBe(otherValue: Any) = must(be(otherValue))

  /** alias for <code>must be(other)</code>  */
  def mustEq(otherValue: Any) = must(be(otherValue))

  /** alias for <code>must notEq(other)</code>  */
  def mustNotBe(otherValue: Any) = must(notEq(otherValue))

  /** alias for <code>must notEq(other)</code>  */
  def mustNotEq(otherValue: Any) = mustNotBe(otherValue)

  /** alias for <code>must is_!=(other)</code>  */
  def must_!=(otherValue: Any)(implicit details: Detailed) = must(is_!=(otherValue)(details))

  /** alias for <code>must is_==(other)</code>  */
  def must_==(otherValue: Any)(implicit details: Detailed) = {
    must(is_==(otherValue)(details))
  }

  /** alias for <code>must is_==(other)</code>  */
  def mustEqual(otherValue: Any)(implicit details: Detailed) = must(is_==(otherValue)(details))
}

/** Specialized expectable class with string matchers aliases */
class StringExpectable[A <: String](value: => A) extends Expectable[A](value) {

  def createClone = new StringExpectable(value)
  /** alias for <code>must(beMatching(a))</code> */
  def mustMatch(a: String) = applyMatcher(beMatching(a))

  /** alias for <code>must(not(beMatching(a)))</code> */
  def mustNotMatch(a: String) = applyMatcher(not(beMatching(a)))

  /** alias for <code>must(beEqualToIgnoringCase(a))</code> */
  def must_==/(a: String) = applyMatcher(beEqualToIgnoringCase(a))

  /** alias for <code>must(notBeEqualToIgnoringCase(a))</code> */
  def must_!=/(a: String) = applyMatcher(notBeEqualToIgnoringCase(a))
}
/** Specialized expectable class with iterable matchers aliases */
class IterableExpectable[I <: AnyRef](value: =>Iterable[I]) extends Expectable[Iterable[I]](value) {
  def createClone = new IterableExpectable(value)

  /** alias for <code>must(exist(function(_))</code> */
  def mustHave(function: I => Boolean) = applyMatcher(have(function((_:I))))

  /** alias for <code>must(notExist(function(_))</code> */
  def mustNotHave(function: I => Boolean) = applyMatcher(notHave(function((_:I))))

  /**
   * alias for <code>must(exist(function(_))</code>
   * @deprecated use mustHave instead
   */
  def mustExist(function: I => Boolean) = mustHave(function)

  /**
   * alias for <code>must(notExist(function(_))</code>
   * @deprecated use mustNotHave instead
   */
  def mustNotExist(function: I => Boolean) = mustNotHave(function)

  /** alias for <code>must(contain(a))</code> */
  def mustContain(elem: I) = applyMatcher(contain(elem))

  /** alias for <code>must(notContain(a))</code> */
  def mustNotContain(elem: I) = applyMatcher(notContain(elem))
}
/** Specialized expectable class with iterable[String] matchers aliases */
class IterableStringExpectable(value: =>Iterable[String]) extends Expectable[Iterable[String]](value) {
  def createClone = new IterableStringExpectable(value)

  /** alias for <code>must(containMatch(pattern))</code> */
  def mustContainMatch(pattern: String) = applyMatcher(containMatch(pattern))

  /** alias for <code>must(notContainMatch(pattern))</code> */
  def mustNotContainMatch(pattern: String) = applyMatcher(notContainMatch(pattern))

  /** alias for <code>must(containMatch(pattern))</code> */
  def mustHaveMatch(pattern: String) = applyMatcher(containMatch(pattern))

  /** alias for <code>must(notContainMatch(pattern))</code> */
  def mustNotHaveMatch(pattern: String) = applyMatcher(notContainMatch(pattern))

  /**
   * alias for <code>must(containMatch(pattern))</code>
   * @deprecated: use mustContainMatch or mustHaveMatch instead
   */
  def mustExistMatch(pattern: String) = applyMatcher(containMatch(pattern))

  /**
   * alias for <code>must(notContainMatch(pattern))</code>
   * @deprecated: use mustNotContainMatch or mustNotHaveMatch instead
   */
  def mustNotExistMatch(pattern: String) = applyMatcher(notContainMatch(pattern))
}
/**
 * By default the result value of an expectable expression doesn't output anything when
 * toString is called.
 */
/**
 * This trait transforms SuccessValue objects to a Boolean value if it is necessary, for example in
 * ScalaCheck properties.
 */
trait SuccessValues {

  /** transforms a SuccessValue to a boolean */
  implicit def successValueToBoolean(s: SuccessValue) = true

  /** by default a SuccessValue is "silent" */
  def successValueToString(s: SuccessValue) = ""

}
/** value returned by an expectable whose string representation can vary. */
trait SuccessValue
class Result[T](expectable: => Expectable[T], f: SuccessValue => String) extends SuccessValue {
  override def toString = f(this)
  def matchWith[S >: T](m: => Matcher[S]) = expectable.applyMatcher(m)
  def be(m: => Matcher[T]) = expectable.applyMatcher(m)
  def have(m: => Matcher[T]) = expectable.applyMatcher(m)
  def apply(m: => Matcher[T]) = expectable.applyMatcher(m)
}
