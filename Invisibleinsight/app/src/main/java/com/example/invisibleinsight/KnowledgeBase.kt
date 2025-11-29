package com.example.invisibleinsight

object KnowledgeBase {
    
    // Simple RAG: Map of Keywords -> Response
    // We will check if the user input contains *any* of the keywords in a key set.
    // Order matters: Higher priority entries should be first if we iterate.
    
    val entries = mapOf(
        // --- Game Modes ---
        listOf("gyro mode", "tilt mode", "gyroscope") to 
            "In Gyro Mode, tilt your device to move the spirit. The more you tilt, the faster it moves.",
            
        listOf("touch mode", "tap mode", "finger") to 
            "In Touch Mode, tap anywhere on the screen. The spirit will move towards your finger.",
            
        listOf("absolute mode", "absolute control") to 
            "In Absolute Mode, the spirit's position directly maps to your tilt angle, like a marble on a tray.",

        // --- Levels ---
        listOf("level 1", "level one", "first level") to 
            "Level 1 is a straight path to get you started. Just follow the continuous beacon sound.",
            
        listOf("level 2", "level two", "second level") to 
            "Level 2 introduces corners and walls. Listen to the pitch of the wall pings to know which way is blocked.",
            
        listOf("level 3", "level three", "third level") to 
            "Level 3 is a large scrolling map with dangerous spikes. If you hear a siren, stop moving!",

        // --- Game Instructions ---
        listOf("how to play", "instructions", "rules", "guide", "tutorial") to 
            "Navigate the invisible maze using sound. Listen to the sonar pings. A continuous tone leads to the goal.",
            
        listOf("goal", "win", "exit", "finish") to 
            "The goal emits a pleasant, continuous cosmo-like sound. Follow it to win.",
            
        listOf("wall", "obstacle", "hit") to 
            "Walls emit sonar pings. High pitch means up or right, low pitch means down or left. The faster the ping, the closer you are.",
            
        listOf("spike", "siren", "alarm", "danger") to 
            "Spikes are dangerous obstacles. They emit a siren sound. Avoid them at all costs.",
            
        listOf("nova", "who are you", "what are you") to 
            "I am Nova, your AI guide. I can help you with game instructions or tell you stories.",
            
        listOf("lives", "heart", "health") to 
            "You have 5 lives. Hitting a wall or spike costs one life and respawns you at the last checkpoint.",
            
        listOf("checkpoint", "save", "respawn") to 
            "Checkpoints are invisible save spots. If you lose a life, you will reappear at the nearest one.",
            
        // --- General Utilities ---
        listOf("time", "clock", "hour") to 
            "TIME_PLACEHOLDER", // Dynamic replacement
            
        listOf("date", "day", "today") to 
            "DATE_PLACEHOLDER", // Dynamic replacement
            
        // --- Motivation ---
        listOf("stuck", "hard", "difficult", "cant do it") to 
            "Don't give up! Listen closely to the rhythm of the walls. You can do this.",
            
        listOf("bored", "tired") to 
            "Take a deep breath. Focus on the sounds. The exit is closer than you think.",
            
        listOf("fun", "cool", "awesome", "great") to 
            "I am glad you are enjoying the experience! Keep going.",
            
        // --- Easter Eggs ---
        listOf("hello", "hi", "hey", "greetings") to 
            "Hello there, traveler. Ready to navigate the unseen?",
            
        listOf("joke", "funny") to 
            "Why did the player hit the wall? Because they didn't listen! Ha ha.",
            
        listOf("developer", "creator", "made this") to 
            "This game was created to test your auditory senses. Good luck!"
    )
    
    fun search(query: String): String? {
        val lowerQuery = query.lowercase()
        
        for ((keywords, response) in entries) {
            // Check if *any* keyword in the list is present in the query
            if (keywords.any { lowerQuery.contains(it) }) {
                return response
            }
        }
        return null
    }
}
