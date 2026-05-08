is PlayState.Ready -> {
    vm.clearPlayState()
    startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
        putExtra("url", state.url)
        putExtra("isDash", state.isDash)
    })
}
