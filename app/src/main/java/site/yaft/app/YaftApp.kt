package site.yaft.app

import android.app.Application
import site.yaft.app.data.SessionStore
import site.yaft.app.net.Account
import site.yaft.app.net.Uploader

class YaftApp : Application() {
    lateinit var account: Account; private set
    lateinit var uploader: Uploader; private set

    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
        account = Account(this)
        uploader = Uploader(account)
    }
}
