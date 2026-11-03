#!/usr/bin/env python3
"""Signs controlled fixture JWTs with the local demo key to test actual resource-server validation."""
import base64,json,subprocess,time,urllib.request,urllib.error
from pathlib import Path
root=Path(__file__).resolve().parents[1]
http=urllib.request.build_opener(urllib.request.ProxyHandler({}))
keys=json.load(http.open('http://127.0.0.1:9000/oauth2/jwks'))
kid=keys['keys'][0]['kid']
def encode(value):return base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=')
def token(overrides):
    now=int(time.time());claims={'iss':'http://127.0.0.1:9000','sub':'test-subject','aud':['flight-api'],'iat':now,'nbf':now-5,'exp':now+120,'scope':['flights.read'],'roles':['VIEWER'],'terminals':['T1']}|overrides
    message=encode({'alg':'RS256','kid':kid})+b'.'+encode(claims)
    sig=subprocess.check_output(['openssl','dgst','-sha256','-sign',str(root/'.runtime/signing.pem')],input=message)
    return (message+b'.'+base64.urlsafe_b64encode(sig).rstrip(b'=')).decode()
for name,overrides,expected in [('valid',{},200),('wrong-audience',{'aud':['other-api']},401),('wrong-issuer',{'iss':'http://untrusted'},401),('expired',{'exp':int(time.time())-300},401),('missing-role',{'roles':[]},403),('missing-read-scope',{'scope':[]},403)]:
    req=urllib.request.Request('http://localhost:8082/api/v1/flights',headers={'Authorization':'Bearer '+token(overrides)})
    try:response=http.open(req);status=response.status
    except urllib.error.HTTPError as e:status=e.code
    assert status==expected,(name,status,expected)
    print('PASS JWT boundary:',name,status)
