package io.javafast.internal

import spock.lang.Specification

class StringMapSpec extends Specification {

    def "should add entries and preserve insertion order when capacity grows"() {
        given:
        def map = new StringMap(0)

        when:
        map.put('first', '1')
        map.put('second', '2')
        map.put('third', '3')

        then:
        map.size() == 3
        !map.isEmpty()
        map.keyAt(0) == 'first'
        map.valueAt(0) == '1'
        map.keyAt(1) == 'second'
        map.valueAt(1) == '2'
        map.keyAt(2) == 'third'
        map.valueAt(2) == '3'
    }

    def "should update an existing key without increasing size"() {
        given:
        def map = new StringMap(0)
        map.put('Name', 'old')

        when:
        map.put('name', 'new')

        then:
        map.size() == 1
        map.get('NAME') == 'new'
        map.keyAt(0) == 'Name'
        map.valueAt(0) == 'new'
    }

    def "should remove an existing key without scooting"() {
        given:
        def map = new StringMap(1)

        when:
        map.put('Name', 'new')
        then:
        map.size() == 1
        map.get('NAME') == 'new'
        map.keyAt(0) == 'Name'
        map.valueAt(0) == 'new'

        when:
        map.remove('name')
        then:
        map.size() == 1
        map.get('NAME') == null
        map.keyAt(0) == 'Name'
        map.valueAt(0) == null
    }

    def "should return null and false for an unknown key"() {
        given:
        def map = new StringMap(0)
        map.put('known', 'value')

        expect:
        map.get('unknown') == null
        !map.containsKey('unknown')
    }

    def "should accept a null key"() {
        given:
        def map = new StringMap(0)

        when:
        map.put(null, 'value')

        then:
        map.size() == 1
        map.containsKey(null)
        map.get(null) == 'value'
        map.keyAt(0) == null
        map.valueAt(0) == 'value'
    }

    def "should accept a null value"() {
        given:
        def map = new StringMap(0)

        when:
        map.put('key', null)

        then:
        map.size() == 1
        map.containsKey('key')
        map.get('key') == null
        map.keyAt(0) == 'key'
        map.valueAt(0) == null
    }

    def "should clear all entries and become empty"() {
        given:
        def map = new StringMap(0)
        map.put('one', '1')
        map.put(null, null)

        when:
        map.clear()

        then:
        map.size() == 0
        map.isEmpty()
        !map.containsKey('one')
        !map.containsKey(null)
        map.get('one') == null
        map.get(null) == null
    }

    def "should reject indexes outside the current entry range for keys and values"() {
        given:
        def map = new StringMap(0)

        when:
        if (accessor == 'key') {
            map.keyAt(index)
        } else {
            map.valueAt(index)
        }

        then:
        thrown(IndexOutOfBoundsException)

        where:
        accessor << ['key', 'value']
        index << [-1, 0]
    }
}
