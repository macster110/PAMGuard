Apple require developers to sign their executable and then send it top them to be checked. The thing is they want every single line of code signed sand for a jar that is very difficult. 

```./build_and_sign.sh``` was an attempt to fix this by signing everything in the file but it has never worked and PAMGuard is alway rejected using the tool with a list of errors around bits and pieces in the file not being signed proeprly. 

To see any errors from the last code sign use 

```bash
xcrun notarytool log 70e41f0c-0cb1-40af-917a-a29ec07ca2a4 \
     --apple-id "macster110@gmail.com" \
     --password "aqnu-fcyf-itol-dsgq" \
     --team-id "7365S9DZ34"
```
 
 To verify that code signing has worked use (without the notarization)- both the .app and .dmg should be signed. 

```bash
codesign --verify --verbose /Users/jdjm/git/PAMGuard/target/Pamguard-2.02.17ffd-signed.dmg
```