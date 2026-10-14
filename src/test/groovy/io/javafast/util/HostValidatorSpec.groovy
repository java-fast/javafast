package io.javafast.util


import io.javafast.test.TestUtils
import spock.lang.Specification
import spock.lang.Unroll

class HostValidatorSpec extends Specification implements TestUtils {

    @Unroll
    def "accepts valid Host value: '#host'"() {
        expect:
        HostValidator.isValidHost(byteString(host))

        where:
        host << [
          'example.com',
          'EXAMPLE.COM',
          'sub.example.com',
          'localhost',
          'localhost.localdomain',
          'example.com.',
          'example123.com',
          '192.168.1.1',
          '127.0.0.1',
          '0.0.0.0',
          'example.com:80',
          'example.com:443',
          'localhost:8080',
          '192.168.1.1:65535',
          'example.com:0',
          '[::1]',
          '[::1]:8080',
          '[2001:db8::1]',
          '[2001:DB8::1]:443',
          //'[::]',
          //'[2001:db8:0:0:0:0:0:1]',
          //'[::ffff:192.0.2.1]'
        ]
    }

    @Unroll
    def "rejects invalid Host value: '#host'"() {
        expect:
        !HostValidator.isValidHost(byteString(host))

        where:
        host << [
          '',
          ' ',
          '\t',
          'example .com',
          ' example.com',
          'example.com ',
          'example.com\t',
          'http://example.com',
          'https://example.com',
          'user@example.com',
          'example.com/path',
          'example.com?query',
          'example.com#fragment',
          'example.com,other.com',
          'example.com:abc',
          'example.com:-1',
          'example.com:65536',
          'example.com:',
          ':8080',
          'example.com:80:90',
          //'192.168.1.999',
          //'256.256.256.256',
          //'example..com',
          //'-example.com',
          //'example-.com',
          '[::1',
          '::1',
          '[::1]]',
          '[:::1]',
          '[1:2:3:4:5:6:7]',
          '[1:2:3:4:5:6:7:8:9:10]',
          '[2001:db8::1::2]',
          '[gggg::1]',
          '[::1]:',
          '[::1]:abc',
          '[::1]:65536',
          '[::1]extra',
          '[::1]:80/path'
        ]
    }

}
