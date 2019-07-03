package com.beust.kash

import com.beust.kash.Exec.Companion.toExec

//val idTransformer: (Token.SimpleCmd, String) -> String
//        = {word: Token.SimpleCmd, s: String -> s }

class Parser(private val transform: TokenTransform) {

    companion object Parser {
        val wordCharacters = setOf('-', '$', '.', '~', '/', '=', '*', '?', '_')
        fun isWord(c: Char) = c.isLetterOrDigit() || wordCharacters.contains(c)
    }

    fun lexicalParse(line: String, inParen: Boolean = false): List<Token> {

        val result = arrayListOf<Token>()
        var currentWord: StringBuilder? = null
        var index = 0
        while (index < line.length) {
            val c = line[index]
            fun processQuote(quote: Char) {
                index++
                val start = index
                while (index < line.length && line[index] != quote) index++
                val word = StringBuilder(line.substring(start, index))
                result.add(Token.Word(word, quote.toString()))
                index++
            }

            if (c == '\'' || c == '\"' || c == '`') {
                processQuote(c)
                continue
            }

            if (isWord(c)) {
                if (c == '2') {
                    if (index < line.length - 1 && line[index + 1] == '>') {
                        result.add(Token.TwoGreater())
                        index += 2
                        continue
                    } else {
                        // do nothing, keep parsing
                    }
                }
                if (currentWord == null) {
                    currentWord = StringBuilder().append(c)
                    result.add(Token.Word(currentWord!!))
                } else {
                    currentWord.append(c)
                }
            } else {
                currentWord = null
                if (c == ' ' || c == '\t') {
                    // ignore
                } else if (c == ';') {
                    result.add(Token.SemiColon())
                } else if (c == '|') {
                    result.add(Token.Pipe())
                } else if (c == '&') {
                    if (index < line.length - 1 && line[index + 1] == '&') {
                        result.add(Token.AndAnd())
                        index++ // skip next
                    } else {
                        result.add(Token.And())
                    }
                } else if (c == '>') {
                    if (index < line.length - 1 && line[index + 1] == '>') {
                        result.add(Token.GreaterGreater())
                        index++ // skip next
                    } else {
                        result.add(Token.Greater())
                    }
                } else if (c == '<') {
                    result.add(Token.Less())
                } else if (c == '(') {
                    result.add(Token.LeftParenthesis())
                } else if (c == ')') {
                    result.add(Token.RightParenthesis())
                }
            }
            index++
        }

        return result
    }

    fun parse(line: String): List<Command> = parseTokens(lexicalParse(line))

    private fun parseTokens(tokens: List<Token>,
            pipeList: ArrayList<List<Token>> = arrayListOf<List<Token>>(),
            andList: ArrayList<List<Token>> = arrayListOf<List<Token>>()
    ): List<Command> {
        val result = arrayListOf<Command>()
        var index = 0

        var done = false
        while (! done && index < tokens.size) {
            val current = arrayListOf<Token>()

            fun flushInput() {
                if (pipeList.isNotEmpty()) {
                    pipeList.add(current)
                    result.add(buildPipeList(pipeList))
                    pipeList.clear()
                } else if (andList.isNotEmpty()) {
                    andList.add(current)
                    result.add(buildAndList(andList))
                    andList.clear()
                } else {
                    if (current.isNotEmpty()) {
                        result.add(buildCommand(current))
                    }
                }
            }

            // Skip non words
            while (index < tokens.size && ! tokens[index].isWord && tokens[index] != Token.LeftParenthesis()) index++

            // Parse words
            while (! done && index < tokens.size && ! tokens[index].isSeparator) {
                if (tokens[index] == Token.LeftParenthesis()) {
                    if (current.isNotEmpty()) {
                        // "foo(" is illegal shell syntax, must be a Kotlin expression
                        current.clear()
                        done = true
                    } else {
                        val right = tokens.indexOfFirst() { it == Token.RightParenthesis() }
                        if (right == -1) {
                            throw ShellException("Missing closed parenthesis")
                        } else {
                            val parenContent = tokens.subList(index + 1, right)
                            if (parenContent.isNotEmpty()) {
                                val subCommands = parseTokens(parenContent, pipeList, andList)
                                subCommands.forEach {
                                    index += it.tokens.size + 2
                                    val background =
                                            if (index < tokens.size - 1 && tokens[index + 1] == Token.And()) {
                                                index++
                                                true
                                            } else {
                                                false
                                            }
                                    result.add(Command.ParenCommand(it, background))
                                }
                            }
                        }
                    }
                } else {
                    current.add(tokens[index])
                }
                index++
            }
            if (index < tokens.size) when (tokens[index]) {
                is Token.Pipe -> pipeList.add(current)
                is Token.AndAnd -> andList.add(current)
                else -> {
                    flushInput()
                }
            } else {
                flushInput()
            }
            index++
        }
        return result
    }

    private fun buildAndList(andList: List<List<Token>>): Command.AndCommands {
        val background = false
        return Command.AndCommands(andList.map { toExec(it, transform) }, background)
    }

    private fun buildPipeList(pipeList: List<List<Token>>)
            = Command.PipeCommands(pipeList.map { toExec(it, transform) }, false)

    private fun buildCommand(tokens: List<Token>): Command.SingleCommand {
        val background = tokens[tokens.size - 1] == Token.And()
        return Command.SingleCommand(toExec(tokens, transform), background)
    }
}
