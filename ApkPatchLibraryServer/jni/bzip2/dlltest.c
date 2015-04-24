/*
   minibz2
      libbz2.dll test program.
      by Yoshioka Tsuneo (tsuneo@rr.iij4u.or.jp)
      This file is Public Domain.  Welcome any email to me.

   usage: minibz2 [-d] [-{1,2,..9}] [[srcfilename] destfilename]
*/

#define BZ_IMPORT
#include <stdio.h>
#include <stdlib.h>
#include "bzlib.h"
#ifdef _WIN32
#include <io.h>
#endif


#ifdef _WIN32

#define BZ2_LIBNAME "libbz2-1.0.2.DLL" 

#include <windows.h>
static int BZ2DLLLoaded = 0;
static HINSTANCE BZ2DLLhLib;
int BZ2DLLLoadLibrary(void)
{
   HINSTANCE hLib;

   if(BZ2DLLLoaded==1){return 0;}
   hLib=LoadLibrary(BZ2_LIBNAME);
   if(hLib == NULL){
      fprintf(stderr,"Can't load %s\n",BZ2_LIBNAME);
      return -1;
   }
   BZ2_bzlibVersion=GetProcAddress(hLib,"BZ2_bzlibVersion");
   BZ2_bzopen=GetProcAddress(hLib,"BZ2_bzopen");
   BZ2_bzdopen=GetProcAddress(hLib,"BZ2_bzdopen");
   BZ2_bzread=GetProcAddress(hLib,"BZ2_bzread");
   BZ2_bzwrite=GetProcAddress(hLib,"BZ2_bzwrite");
   BZ2_bzflush=GetProcAddress(hLib,"BZ2_bzflush");
   BZ2_bzclose=GetProcAddress(hLib,"BZ2_bzclose");
   BZ2_bzerror=GetProcAddress(hLib,"BZ2_bzerror");

   if (!BZ2_bzlibVersion || !BZ2_bzopen || !BZ2_bzdopen
       || !BZ2_bzread || !BZ2_bzwrite || !BZ2_bzflush
       || !BZ2_bzclose || !BZ2_bzerror) {
      fprintf(stderr,"GetProcAddress failed.\n");
      return -1;
   }
   BZ2DLLLoaded=1;
   BZ2DLLhLib=hLib;
   return 0;

}
int BZ2DLLFreeLibrary(void)
{
   if(BZ2DLLLoaded==0){return 0;}
   FreeLibrary(BZ2DLLhLib);
   BZ2DLLLoaded=0;
}
#endif /* WIN32 */

void usage(void)
{
   puts("usage: minibz2 [-d] [-{1,2,..9}] [[srcfilename] destfilename]");
}

