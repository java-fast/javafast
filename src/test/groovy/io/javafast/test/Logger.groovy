package io.javafast.test

trait Logger {

    static boolean logEnabled = Boolean.parseBoolean(System.getProperty('test.logger'))

    def printMethodStart() {
        if (!logEnabled) return
        println('/' + '-'.repeat(100) + '/')
        def message = "${getClass().getName()}: "
        print('/ ' + message)
        println(' '.repeat(100 - message.length() - 1) + '/')
        println('/' + '-'.repeat(100) + '/')
    }

    def printMethodEnd() {
        if (!logEnabled) return
        println('/' + '-'.repeat(100) + '/\n')
    }

    def log(String message) {
        if (logEnabled) println('/ -> ' + message)
    }

}
