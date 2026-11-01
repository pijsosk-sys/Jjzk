#!/usr/bin/env python3
"""Adds StudioSignInActivity to the Boddy app's manifest (decoded by apktool): the phone's browser hands it Roblox
Studio's sign-in link (roblox-studio-auth:...) once the user has signed in there.
Usage: patch_manifest.py APKTOOL_DIR/AndroidManifest.xml"""
import sys
path=sys.argv[1];s=open(path).read()
if 'StudioSignInActivity' in s:sys.exit('Manifest already has StudioSignInActivity')
activity='''        <activity android:name="com.boddy.os.StudioSignInActivity" android:exported="true" android:excludeFromRecents="true" android:noHistory="true" android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="roblox-studio-auth"/>
            </intent-filter>
        </activity>
'''
if s.count('</application>')!=1:sys.exit('Unexpected manifest: no single </application>')
s=s.replace('    </application>',activity+'    </application>')
open(path,'w').write(s);print('Added StudioSignInActivity to',path)
