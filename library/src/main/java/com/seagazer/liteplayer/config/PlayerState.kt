package com.seagazer.liteplayer.config

/**
 * Player state define.
 *
 * Author: Seagazer
 * Date: 2020/6/20
 */
enum class PlayerState {
    /* Begin: state for player */
    STATE_NOT_INITIALIZED,
    STATE_INITIALIZED,
    STATE_PREPARED,
    STATE_STARTED,
    STATE_PAUSED,
    STATE_STOPPED,
    STATE_PLAYBACK_COMPLETE,
    STATE_ERROR,
    /* End: state for player */

    STATE_BUFFER_UPDATE,
    STATE_BUFFER_START,
    STATE_BUFFER_END,
    STATE_SEEK_START,
    STATE_SEEK_COMPLETED,

    STATE_VIDEO_SIZE_CHANGED,
    STATE_SURFACE_SIZE_CHANGED,
    STATE_RENDERED_FIRST_FRAME,



}