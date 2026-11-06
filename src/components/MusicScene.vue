<template>
  <div class="music-scene" aria-hidden="true">
    <div class="music-orbit orbit-one"></div><div class="music-orbit orbit-two"></div>
    <span class="music-star star-one">✳</span><span class="music-star star-two">✦</span>
    <div class="record-sleeve"><span>SHOPFLOW SESSIONS</span><strong>SOUND<br />IS A<br /><i>FEELING.</i></strong><small>SIDE A · YOUR NEXT CHAPTER</small></div>
    <div class="vinyl-record"><div class="record-label"><span>SF</span><small>PLAY YOUR STORY</small><i></i></div></div>
    <svg class="scene-guitar" viewBox="0 0 160 410" fill="none">
      <path d="M68 126c-29-2-43 18-37 43 4 14 0 22-10 35-34 41-16 96 36 109 58 15 100-22 88-71-4-17-20-30-25-43-7-18 2-26-3-43-6-21-26-31-49-30Z" fill="#df9467" stroke="#f3bf95" stroke-width="3" />
      <path d="M55 139c-18 1-25 17-18 33 7 18-3 28-14 44-24 35-8 74 29 87" stroke="#ab6345" stroke-width="3" />
      <path d="m72 33 24 2-6 161-24-1z" fill="#523d31" stroke="#ddb58b" stroke-width="2" />
      <path d="m72 33-3-29 33 2-6 29" fill="#df9467" stroke="#f3bf95" stroke-width="2" />
      <path d="m68 10-7-1m7 11-7-1m40-8 7 1m-8 9 7 1" stroke="#e5e9df" stroke-width="5" stroke-linecap="round" />
      <circle cx="77" cy="207" r="25" fill="#3b3029" stroke="#efc499" stroke-width="5" /><circle cx="77" cy="207" r="29" stroke="#a96143" />
      <path d="m54 264 42 2" stroke="#523d31" stroke-width="9" stroke-linecap="round" />
      <path d="M76 31 70 264M80 31l-5 234M84 32l-4 233M88 32l-3 233M92 32l-2 233" stroke="#fff1cb" stroke-width=".65" />
      <path d="m73 56 22 1m-23 14 22 1m-22 14 21 1m-21 14 21 1m-22 14 21 1m-21 14 20 1m-21 14 20 1" stroke="#d1b18b" />
    </svg>
    <div class="sound-card"><span class="sound-bars"><i v-for="bar in 9" :key="bar" :style="{ '--bar': bar }"></i></span><span><strong>Find your rhythm</strong><small>Âm nhạc bắt đầu từ bạn.</small></span></div>
    <div class="scene-caption"><span class="live-dot"></span> MADE FOR YOUR NEXT MELODY <span>↗</span></div>
  </div>
</template>
