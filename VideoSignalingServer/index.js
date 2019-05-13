'use strict';

var os = require('os');
var nodeStatic = require('node-static');
var https = require('https');
var socketIO = require('socket.io');
var fs = require("fs");
var options = {
	key: fs.readFileSync('key.pem'),
	cert: fs.readFileSync('cert.pem')
};

var port = 1794;
var fileServer = new(nodeStatic.Server)();
var app = https.createServer(options,function(req, res) {
	fileServer.serve(req, res);
}).listen(port);

console.log("Server is listening on port " + port + " ...");

var io = socketIO.listen(app);
io.sockets.on('connection', function(socket) {
	// convenience function to log server messages on the client
	function log() {
		var array = [];
		array.push.apply(array, arguments);
		console.log(array[0], array[1]);
		socket.emit('log', array);
	}

	socket.on('message', function(room, message) {
		log('[Client ' + socket.id + ']', message);
		socket.broadcast.to(room).emit('message', message);
	});

	socket.on('create or join', function(room) {
		log('[Server]', 'Received request from Client ' + socket.id + ' to create or join room ' + room);
		
		var room_object = io.sockets.adapter.rooms[room];
		var numClients;
		if (room_object) {
			numClients = Object.keys(room_object).length;
		}   

		if (!room_object) {
			socket.join(room);
			log('[Server]', 'Client ' + socket.id + ' created room ' + room);
			socket.emit('created', room, socket.id);
			room_object = io.sockets.adapter.rooms[room];
			numClients = Object.keys(room_object).length;
			log('[Server]', 'Room ' + room + ' now has ' + numClients + ' client(s)');
		} else if (numClients === 1) {
			log('[Server]', 'Client ' + socket.id + ' joined room ' + room);
			io.sockets.in(room).emit('join', room);
			socket.join(room);
			socket.emit('joined', room, socket.id);
			numClients = Object.keys(room_object).length;
			log('[Server]', 'Room ' + room + ' now has ' + numClients + ' client(s)');
			io.sockets.in(room).emit('ready');
		} else { // max 2 clients
			socket.emit('full', room);
			log('[Server]', 'Room ' + room + ' is full.');
		}
	});

	socket.on('bye', function(room){
		log('[Server]', 'Client ' + socket.id + ' left room ' + room);
		socket.broadcast.to(room).emit('message', 'bye');
	});
});
